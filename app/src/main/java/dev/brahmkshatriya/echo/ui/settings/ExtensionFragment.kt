package dev.brahmkshatriya.echo.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingItem
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.plugger.ExtensionMetadata
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.plugger.MusicExtension
import dev.brahmkshatriya.echo.plugger.getExtension
import dev.brahmkshatriya.echo.viewmodels.ExtensionViewModel

class ExtensionFragment : BaseSettingsFragment() {
    private val name by lazy { requireArguments().getString("name")!! }
    private val id by lazy { requireArguments().getString("id")!! }
    private val type by lazy { requireArguments().getString("type")!! }
    override val title get() = name
    override val transitionName get() = id
    override val creator = { ExtensionPreference.newInstance(id, type) }

    companion object {
        fun newInstance(name: String, id: String, type: ExtensionType): ExtensionFragment {
            val bundle = Bundle().apply {
                putString("name", name)
                putString("id", id)
                putString("type", type.name)
            }
            return ExtensionFragment().apply {
                arguments = bundle
            }
        }

        fun newInstance(extensionMetadata: ExtensionMetadata, extensionType: ExtensionType) =
            newInstance(extensionMetadata.name, extensionMetadata.id, extensionType)

        fun newInstance(extension: MusicExtension) =
            newInstance(extension.metadata, ExtensionType.MUSIC)

    }

    class ExtensionPreference : PreferenceFragmentCompat() {

        private val extensionId: String by lazy {
            requireArguments().getString("extensionId")!!
        }
        private val extensionType: ExtensionType by lazy {
            val type = requireArguments().getString("extensionType")!!
            ExtensionType.valueOf(type)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            preferenceManager.sharedPreferencesName = extensionId
            preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = screen


            val viewModel by activityViewModels<ExtensionViewModel>()
            val client = viewModel.run {
                when (extensionType) {
                    ExtensionType.MUSIC -> extensionListFlow.getExtension(extensionId)?.client
                    ExtensionType.TRACKER -> trackerListFlow.getExtension(extensionId)?.client
                    ExtensionType.LYRICS -> lyricsListFlow.getExtension(extensionId)?.client
                }
            }

            client ?: return
            client.settingItems.forEach { setting ->
                setting.addPreferenceTo(screen)
            }
        }

        private fun Setting.addPreferenceTo(preferenceGroup: PreferenceGroup) {
            when (this) {
                is SettingCategory -> {
                    PreferenceCategory(preferenceGroup.context).also {
                        it.title = this.title
                        it.key = this.key

                        it.isIconSpaceReserved = false
                        it.layoutResource = R.layout.preference_category
                        preferenceGroup.addPreference(it)

                        this.items.forEach { item ->
                            item.addPreferenceTo(it)
                        }
                    }
                }

                is SettingItem -> {
                    Preference(preferenceGroup.context).also {
                        it.title = this.title
                        it.key = this.key
                        it.summary = this.summary

                        it.isIconSpaceReserved = false
                        it.layoutResource = R.layout.preference
                        preferenceGroup.addPreference(it)
                    }
                }

                is SettingSwitch -> {
                    SwitchPreference(preferenceGroup.context).also {
                        it.title = this.title
                        it.key = this.key
                        it.summary = this.summary
                        it.setDefaultValue(this.defaultValue)

                        it.isIconSpaceReserved = false
                        it.layoutResource = R.layout.preference_switch
                        preferenceGroup.addPreference(it)
                    }
                }

                is SettingList -> {
                    ListPreference(preferenceGroup.context).also {
                        it.title = this.title
                        it.key = this.key
                        defaultEntryIndex?.let { index ->
                            it.setDefaultValue(this.entryValues[index])
                            it.summary = this.entryTitles[index]
                        }
                        it.entries = this.entryTitles.toTypedArray()
                        it.entryValues = this.entryValues.toTypedArray()

                        it.isIconSpaceReserved = false
                        it.layoutResource = R.layout.preference
                        preferenceGroup.addPreference(it)
                    }
                }

                else -> throw IllegalArgumentException("Unsupported setting type")
            }
        }

        companion object {
            fun newInstance(id: String, type: String): ExtensionPreference {
                val bundle = Bundle().apply {
                    putString("extensionId", id)
                    putString("extensionType", type)
                }
                return ExtensionPreference().apply {
                    arguments = bundle
                }
            }
        }
    }
}