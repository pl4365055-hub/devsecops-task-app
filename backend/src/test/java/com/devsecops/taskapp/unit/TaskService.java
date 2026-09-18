package com.devsecops.taskapp.unit;

import com.devsecops.taskapp.entity.Task;
import com.devsecops.taskapp.mapper.TaskMapper;
import com.devsecops.taskapp.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTask_shouldInsertAndReturn() {
        Task task = new Task();
        task.setTitle("Test Task");
        task.setDescription("Test Description");

        when(taskMapper.insert(any(Task.class))).thenReturn(1);

        taskService.create(task);

        verify(taskMapper, times(1)).insert(task);
        assertEquals("Test Task", task.getTitle());
    }

    @Test
    void getById_shouldReturnTask() {
        Task task = new Task();
        task.setId(1L);
        task.setTitle("Existing Task");
        when(taskMapper.selectById(1L)).thenReturn(task);

        Task result = taskService.getById(1L);

        assertNotNull(result);
        assertEquals("Existing Task", result.getTitle());
    }

    @Test
    void delete_shouldCallMapperDelete() {
        taskService.delete(1L);
        verify(taskMapper, times(1)).deleteById(1L);
    }
}
