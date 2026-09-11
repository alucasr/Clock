package org.fossify.clock.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogGroupTitleBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value

/**
 * Dialog to create a new alarm group or rename an existing one.
 * [existingTitles] is used to prevent duplicate group names (case-insensitive),
 * excluding [currentTitle] itself when renaming.
 */
class GroupTitleDialog(
    val activity: SimpleActivity,
    val currentTitle: String? = null,
    val existingTitles: List<String>,
    val callback: (newTitle: String) -> Unit,
) {
    private val binding = DialogGroupTitleBinding.inflate(activity.layoutInflater)

    init {
        binding.groupName.setText(currentTitle ?: "")

        val titleId = if (currentTitle == null) R.string.new_group else R.string.rename_group

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, titleId) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = binding.groupName.value
                        when {
                            newTitle.isEmpty() -> {
                                activity.toast(R.string.group_name_empty)
                            }

                            existingTitles.any {
                                it.equals(newTitle, ignoreCase = true) &&
                                    !it.equals(currentTitle, ignoreCase = true)
                            } -> {
                                activity.toast(R.string.group_already_exists)
                            }

                            else -> {
                                callback(newTitle)
                                alertDialog.dismiss()
                            }
                        }
                    }

                    alertDialog.showKeyboard(binding.groupName)
                }
            }
    }
}
