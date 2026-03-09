package ai.foxtouch.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.foxtouch.data.db.entity.TaskEntity
import ai.foxtouch.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateStatus(taskId: String, status: String) {
        viewModelScope.launch { taskRepository.updateStatus(taskId, status) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { taskRepository.delete(taskId) }
    }
}
