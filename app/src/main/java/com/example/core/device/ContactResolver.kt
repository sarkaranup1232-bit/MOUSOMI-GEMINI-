package com.example.core.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedContact(
  val contactId: String,
  val displayName: String,
  val phoneNumber: String,
  val isPrimary: Boolean = false,
)

sealed interface ContactLookupOutcome {
  data class UniqueMatch(val contact: ResolvedContact) : ContactLookupOutcome
  data class AmbiguousMatches(val query: String, val candidates: List<ResolvedContact>) : ContactLookupOutcome
  data class NotFound(val query: String) : ContactLookupOutcome
  data class PermissionRequired(val query: String) : ContactLookupOutcome
}

/**
 * Privacy-preserving Android Contacts resolver.
 * - Avoids demanding Contacts permission unless the user queries a contact by name.
 * - Handles direct phone numbers without READ_CONTACTS.
 * - Supports zero-permission system Contact Picker URIs via [parsePickedContactUri].
 * - Never logs contact names or phone numbers.
 */
@Singleton
class ContactResolver @Inject constructor(
  @ApplicationContext private val context: Context,
) {

  fun hasContactsPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
      PackageManager.PERMISSION_GRANTED

  fun resolveContact(rawQuery: String): ContactLookupOutcome {
    val clean = rawQuery.trim()
    if (clean.isBlank()) return ContactLookupOutcome.NotFound(rawQuery)

    if (looksLikeDirectPhoneNumber(clean)) {
      val sanitized = PhoneActionController.sanitizePhoneNumber(clean)
      return ContactLookupOutcome.UniqueMatch(
        ResolvedContact(
          contactId = "direct_number",
          displayName = sanitized,
          phoneNumber = sanitized,
          isPrimary = true,
        )
      )
    }

    if (!hasContactsPermission()) {
      return ContactLookupOutcome.PermissionRequired(clean)
    }

    val matches = queryContactsByName(clean)
    return classifyLookupMatches(clean, matches)
  }

  /**
   * Resolves a contact selected via the zero-permission Android system Contact Picker
   * (`ActivityResultContracts.PickContact()` or `Intent.ACTION_PICK`).
   */
  fun parsePickedContactUri(contactOrPhoneUri: Uri): ResolvedContact? =
    runCatching {
      val cr = context.contentResolver
      // Try querying as CommonDataKinds.Phone first, else resolve CONTACT_ID -> Phone
      val phoneProjection =
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER,
          ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
      val directMatches = mutableListOf<ResolvedContact>()
      cr.query(contactOrPhoneUri, phoneProjection, null, null, null)?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val priIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
        while (cursor.moveToNext()) {
          val num = if (numIdx >= 0) PhoneActionController.sanitizePhoneNumber(cursor.getString(numIdx).orEmpty()) else ""
          if (num.length >= 3) {
            directMatches.add(
              ResolvedContact(
                contactId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else "picked",
                displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().ifBlank { num } else num,
                phoneNumber = num,
                isPrimary = priIdx >= 0 && cursor.getInt(priIdx) == 1,
              )
            )
          }
        }
      }
      if (directMatches.isNotEmpty()) {
        return@runCatching directMatches.firstOrNull { it.isPrimary } ?: directMatches.first()
      }

      if (!hasContactsPermission()) return@runCatching null

      var contactId: String? = null
      var displayName = ""
      cr.query(
        contactOrPhoneUri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
        null,
        null,
        null,
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          contactId = cursor.getString(0)
          displayName = cursor.getString(1).orEmpty()
        }
      }
      val cid = contactId ?: return@runCatching null
      cr.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(cid),
        null,
      )?.use { cursor ->
        val items = mutableListOf<ResolvedContact>()
        while (cursor.moveToNext()) {
          val num = PhoneActionController.sanitizePhoneNumber(cursor.getString(0).orEmpty())
          if (num.length >= 3) {
            items.add(
              ResolvedContact(
                contactId = cid,
                displayName = displayName.ifBlank { num },
                phoneNumber = num,
                isPrimary = cursor.getInt(1) == 1,
              )
            )
          }
        }
        return@runCatching items.firstOrNull { it.isPrimary } ?: items.firstOrNull()
      }
      null
    }.getOrNull()

  private fun queryContactsByName(nameQuery: String): List<ResolvedContact> {
    val cleanLower = nameQuery.trim().lowercase()
    val results = mutableListOf<ResolvedContact>()
    val projection =
      arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
      )
    val selection = "LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY}) LIKE ?"
    val args = arrayOf("%$cleanLower%")
    runCatching {
      context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        selection,
        args,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
      )?.use { cursor ->
        while (cursor.moveToNext() && results.size < 25) {
          val cid = cursor.getString(0).orEmpty()
          val dName = cursor.getString(1).orEmpty().trim()
          val rawNum = cursor.getString(2).orEmpty()
          val sanitized = PhoneActionController.sanitizePhoneNumber(rawNum)
          val primary = cursor.getInt(3) == 1
          if (dName.isNotBlank() && sanitized.length >= 3) {
            results.add(
              ResolvedContact(
                contactId = cid,
                displayName = dName,
                phoneNumber = sanitized,
                isPrimary = primary,
              )
            )
          }
        }
      }
    }
    return results.distinctBy { "${it.displayName.lowercase()}|${normalizeDigitsForDedupe(it.phoneNumber)}" }
  }

  companion object {
    fun looksLikeDirectPhoneNumber(input: String): Boolean {
      val trimmed = input.trim()
      if (trimmed.isEmpty()) return false
      val hasLetters = trimmed.any { it.isLetter() }
      val digits = trimmed.count { it.isDigit() }
      return !hasLetters && digits >= 3
    }

    fun normalizeDigitsForDedupe(phone: String): String {
      val digits = phone.filter { it.isDigit() }
      return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun classifyLookupMatches(
      query: String,
      matches: List<ResolvedContact>,
    ): ContactLookupOutcome {
      if (matches.isEmpty()) return ContactLookupOutcome.NotFound(query)
      val qLower = query.trim().lowercase()
      val exactMatches = matches.filter { it.displayName.trim().lowercase() == qLower }
      val pool = if (exactMatches.isNotEmpty()) exactMatches else matches
      val uniqueNumbers = pool.distinctBy { normalizeDigitsForDedupe(it.phoneNumber) }
      if (uniqueNumbers.size == 1) {
        return ContactLookupOutcome.UniqueMatch(uniqueNumbers.first())
      }
      // If a single contact has one super-primary number and matches exact name, still surface options if ambiguous
      val distinctContactIds = uniqueNumbers.map { it.contactId }.distinct()
      if (distinctContactIds.size == 1) {
        val superPrimary = uniqueNumbers.firstOrNull { it.isPrimary }
        if (superPrimary != null) {
          return ContactLookupOutcome.UniqueMatch(superPrimary)
        }
      }
      return ContactLookupOutcome.AmbiguousMatches(query = query, candidates = uniqueNumbers.take(8))
    }
  }
}
