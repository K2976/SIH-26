package com.fitforge.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitforge.app.data.remote.generated.models.CommunityDetail
import com.fitforge.app.data.remote.generated.models.VisibilityEnum
import com.fitforge.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateCommunityViewModel @Inject constructor(
    private val communityRepository: CommunityRepository,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _visibility = MutableStateFlow(VisibilityEnum.`public`)
    val visibility: StateFlow<VisibilityEnum> = _visibility.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onNameChange(value: String) {
        _name.value = value
        if (_errorMessage.value != null) _errorMessage.value = null
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun onVisibilityChange(value: VisibilityEnum) {
        _visibility.value = value
    }

    fun reset() {
        _name.value = ""
        _description.value = ""
        _visibility.value = VisibilityEnum.`public`
        _isSubmitting.value = false
        _errorMessage.value = null
    }

    fun createCommunity(onSuccess: (CommunityDetail) -> Unit) {
        val trimmedName = _name.value.trim()
        if (trimmedName.isEmpty()) {
            _errorMessage.value = "Community name cannot be empty"
            return
        }
        if (trimmedName.length > 100) {
            _errorMessage.value = "Community name cannot exceed 100 characters"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null

            communityRepository.createCommunity(
                name = trimmedName,
                description = _description.value.trim(),
                visibility = _visibility.value,
            ).onSuccess { community ->
                _isSubmitting.value = false
                reset()
                onSuccess(community)
            }.onFailure { error ->
                _isSubmitting.value = false
                _errorMessage.value = error.message ?: "Failed to create community"
            }
        }
    }
}
