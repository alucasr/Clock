package org.fossify.clock.dialogs

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import org.fossify.clock.BuildConfig
import org.fossify.clock.databinding.DialogVersionHistoryBinding
import org.fossify.clock.databinding.ItemVersionHistoryBinding
import org.fossify.clock.models.AppVersionHistory
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff

class VersionHistoryDialog(private val activity: Activity) {
    init {
        val binding = DialogVersionHistoryBinding.inflate(LayoutInflater.from(activity))

        binding.versionHistoryCurrentVersion.text = BuildConfig.VERSION_NAME

        AppVersionHistory.entries.forEach { entry ->
            val itemBinding = ItemVersionHistoryBinding.inflate(
                LayoutInflater.from(activity), binding.versionHistoryList, false
            )
            itemBinding.versionItemVersion.text = "v${entry.version}"
            itemBinding.versionItemDate.text = entry.date
            itemBinding.versionItemChanges.text =
                entry.changes.joinToString("\n") { "• $it" }
            binding.versionHistoryList.addView(itemBinding.root)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }
}
