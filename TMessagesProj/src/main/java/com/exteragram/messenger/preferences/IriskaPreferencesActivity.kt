package com.exteragram.messenger.preferences

import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.exteragram.messenger.*
import com.exteragram.messenger.badges.BadgesController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_stars
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.SelectAnimatedEmojiDialog
import java.util.ArrayList

class IriskaPreferencesActivity : BasePreferencesActivity() {

    enum class IriskaItem {
        GHOST_READ,
        GHOST_TYPING,
        GHOST_STORIES,
        BYPASS_NOFORWARDS,
        DISABLE_FLAG_SECURE,
        BLOCK_SPONSORED,
        ANTI_DELETE,
        SAVE_EDIT_HISTORY,
        CUSTOM_BADGES;

        val id: Int = ordinal + 1
    }

    override fun getTitle(): String = "IriskaGram"

    private fun isIriskaAdmin(): Boolean {
        val myId = UserConfig.getInstance(currentAccount).clientUserId
        if (com.exteragram.messenger.iriskaAdminUserId == 0L) {
            com.exteragram.messenger.iriskaAdminUserId = myId
            return true
        }
        return com.exteragram.messenger.iriskaAdminUserId == myId
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.IriskaGhostModeHeader)))
        items.add(uCheck(IriskaItem.GHOST_READ, getString(R.string.IriskaGhostRead), ghostRead, getString(R.string.IriskaGhostReadDesc)))
        items.add(uCheck(IriskaItem.GHOST_TYPING, getString(R.string.IriskaGhostTyping), ghostTyping, getString(R.string.IriskaGhostTypingDesc)))
        items.add(uCheck(IriskaItem.GHOST_STORIES, getString(R.string.IriskaGhostStories), ghostStories, getString(R.string.IriskaGhostStoriesDesc)))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IriskaBypassHeader)))
        items.add(uCheck(IriskaItem.BYPASS_NOFORWARDS, getString(R.string.IriskaBypassNoForwards), bypassNoForwards, getString(R.string.IriskaBypassNoForwardsDesc)))
        items.add(uCheck(IriskaItem.DISABLE_FLAG_SECURE, getString(R.string.IriskaDisableFlagSecure), disableFlagSecure, getString(R.string.IriskaDisableFlagSecureDesc)))
        items.add(uCheck(IriskaItem.BLOCK_SPONSORED, getString(R.string.IriskaBlockSponsored), blockSponsoredMessages, getString(R.string.IriskaBlockSponsoredDesc)))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IriskaAntiDeleteHeader)))
        items.add(uCheck(IriskaItem.ANTI_DELETE, getString(R.string.IriskaAntiDelete), antiDelete, getString(R.string.IriskaAntiDeleteDesc)))
        items.add(uCheck(IriskaItem.SAVE_EDIT_HISTORY, getString(R.string.IriskaSaveEditHistory), saveEditHistory, getString(R.string.IriskaSaveEditHistoryDesc)))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IriskaCustomBadgesHeader)))
        val count = BadgesController.getCustomBadges().size
        items.add(
            uButton(
                IriskaItem.CUSTOM_BADGES,
                getString(R.string.IriskaCustomBadgeGrantTitle),
                R.drawable.msg_fave,
                value = if (count > 0) "$count" else null,
                subtext = getString(R.string.IriskaCustomBadgeGrantDesc)
            )
        )
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > IriskaItem.entries.size) return
        val clickedItem = IriskaItem.entries[item.id - 1]
        when (clickedItem) {
            IriskaItem.GHOST_READ -> toggleBooleanSettingAndRefresh(item) { ghostRead = it }
            IriskaItem.GHOST_TYPING -> toggleBooleanSettingAndRefresh(item) { ghostTyping = it }
            IriskaItem.GHOST_STORIES -> toggleBooleanSettingAndRefresh(item) { ghostStories = it }
            IriskaItem.BYPASS_NOFORWARDS -> toggleBooleanSettingAndRefresh(item) { bypassNoForwards = it }
            IriskaItem.DISABLE_FLAG_SECURE -> toggleBooleanSettingAndRefresh(item) { disableFlagSecure = it }
            IriskaItem.BLOCK_SPONSORED -> toggleBooleanSettingAndRefresh(item) { blockSponsoredMessages = it }
            IriskaItem.ANTI_DELETE -> toggleBooleanSettingAndRefresh(item) { antiDelete = it }
            IriskaItem.SAVE_EDIT_HISTORY -> toggleBooleanSettingAndRefresh(item) { saveEditHistory = it }
            IriskaItem.CUSTOM_BADGES -> {
                if (!isIriskaAdmin()) {
                    BulletinFactory.of(this@IriskaPreferencesActivity)
                        .createSimpleBulletin(R.raw.info, getString(R.string.IriskaCustomBadgeAdminOnly))
                        .show()
                } else {
                    showCustomBadgeDialog()
                }
            }
        }
    }

    private fun showCustomBadgeDialog() {
        val context = parentActivity ?: return
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(8f), AndroidUtilities.dp(24f), 0)
        }

        val idEdit = EditTextBoldCursor(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            hint = "User / Chat ID or @username"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setText(UserConfig.getInstance(currentAccount).clientUserId.toString())
        }
        layout.addView(idEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0f, 4f, 0f, 8f))

        val emojiLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val emojiEdit = EditTextBoldCursor(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            hint = getString(R.string.IriskaCustomBadgeEmojiHint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(BadgesController.TRUSTED_BADGE.documentId.toString())
        }
        emojiLayout.addView(emojiEdit, LayoutHelper.createLinear(0, 36, 1f, 0, 0, 8, 0))

        val emojiBtn = ImageView(context).apply {
            setImageResource(R.drawable.msg_emoji_smiles)
            setColorFilter(Theme.getColor(Theme.key_dialogIcon, resourceProvider))
            setBackgroundDrawable(Theme.getSelectorDrawable(false))
            scaleType = ImageView.ScaleType.CENTER
            setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f))
            setOnClickListener { anchor ->
                val popup = arrayOfNulls<SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow>(1)
                val dialog = object : SelectAnimatedEmojiDialog(
                    this@IriskaPreferencesActivity,
                    context,
                    false,
                    null,
                    SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS,
                    resourceProvider
                ) {
                    override fun onEmojiSelected(
                        emojiView: View?,
                        documentId: Long?,
                        document: TLRPC.Document?,
                        gift: TL_stars.TL_starGiftUnique?,
                        until: Int?
                    ) {
                        val selectedId = documentId ?: document?.id ?: 0L
                        if (selectedId != 0L) {
                            emojiEdit.setText(selectedId.toString())
                        }
                        popup[0]?.dismiss()
                    }
                }
                val popupWindow = SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(
                    dialog,
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT
                )
                popup[0] = popupWindow
                popupWindow.showAsDropDown(anchor, 0, 0, Gravity.TOP or Gravity.RIGHT)
                popupWindow.dimBehind()
            }
        }
        emojiLayout.addView(emojiBtn, LayoutHelper.createLinear(36, 36, 0f, 0f, 0f, 0f))
        layout.addView(emojiLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0f, 4f, 0f, 8f))

        val numberEdit = EditTextBoldCursor(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            hint = getString(R.string.IriskaCustomBadgeNumberHint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(numberEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0f, 4f, 0f, 8f))

        val textEdit = EditTextBoldCursor(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            hint = getString(R.string.IriskaCustomBadgeTextHint)
        }
        layout.addView(textEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0f, 4f, 0f, 8f))

        fun resolveTargetId(): Long? {
            val raw = idEdit.text.toString().trim()
            val clean = raw.removePrefix("@")
            val direct = clean.toLongOrNull()
            if (direct != null) return direct
            val obj = MessagesController.getInstance(currentAccount).getUserOrChat(clean)
            return when (obj) {
                is TLRPC.User -> obj.id
                is TLRPC.Chat -> -obj.id
                else -> null
            }
        }

        AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(getString(R.string.IriskaCustomBadgeDialogTitle))
            setView(layout)
            setPositiveButton(getString(R.string.IriskaCustomBadgeGrantBtn)) { _, _ ->
                val targetId = resolveTargetId()
                val docId = emojiEdit.text.toString().trim().toLongOrNull() ?: BadgesController.TRUSTED_BADGE.documentId
                val label = textEdit.text.toString().trim()
                val number = numberEdit.text.toString().trim().toIntOrNull()
                if (targetId != null && targetId != 0L) {
                    BadgesController.setCustomBadge(targetId, docId, label, number)
                    listView.adapter?.update(true)
                    BulletinFactory.of(this@IriskaPreferencesActivity)
                        .createSimpleBulletin(R.raw.contact_check, org.telegram.messenger.LocaleController.formatString(R.string.IriskaCustomBadgeGranted, targetId))
                        .show()
                } else {
                    BulletinFactory.of(this@IriskaPreferencesActivity)
                        .createSimpleBulletin(R.raw.info, getString(R.string.IriskaCustomBadgeUserNotFound))
                        .show()
                }
            }
            setNeutralButton(getString(R.string.IriskaCustomBadgePresetsBtn)) { _, _ ->
                val presets = arrayOf(
                    getString(R.string.IriskaCustomBadgePresetTrusted),
                    getString(R.string.IriskaCustomBadgePresetDev),
                    getString(R.string.IriskaCustomBadgePresetSupporter)
                )
                val ids = longArrayOf(
                    BadgesController.TRUSTED_BADGE.documentId,
                    BadgesController.DEV_BADGE.documentId,
                    BadgesController.SUPPORTER_BADGE.documentId
                )
                AlertDialog.Builder(context, resourceProvider)
                    .setTitle(getString(R.string.IriskaCustomBadgePresetTitle))
                    .setItems(presets) { _, which ->
                        val targetId = resolveTargetId() ?: UserConfig.getInstance(currentAccount).clientUserId
                        val number = numberEdit.text.toString().trim().toIntOrNull()
                        BadgesController.setCustomBadge(targetId, ids[which], textEdit.text.toString().trim(), number)
                        listView.adapter?.update(true)
                        BulletinFactory.of(this@IriskaPreferencesActivity)
                            .createSimpleBulletin(R.raw.contact_check, "${presets[which]} → $targetId")
                            .show()
                    }
                    .show()
            }
            setNegativeButton(getString(R.string.IriskaCustomBadgeRemoveBtn)) { _, _ ->
                val targetId = resolveTargetId()
                if (targetId != null && targetId != 0L) {
                    BadgesController.removeCustomBadge(targetId)
                    listView.adapter?.update(true)
                    BulletinFactory.of(this@IriskaPreferencesActivity)
                        .createSimpleBulletin(R.raw.info, org.telegram.messenger.LocaleController.formatString(R.string.IriskaCustomBadgeRemoved, targetId))
                        .show()
                }
            }
            show()
        }
    }
}
