package com.devsecops.taskapp.service;

import com.devsecops.taskapp.entity.Task;
import com.devsecops.taskapp.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    @Autowired
    private TaskMapper taskMapper;

    public List<Task> listAll() { return taskMapper.selectList(null); }
    public Task getById(Long id) { return taskMapper.selectById(id); }
    public void create(Task task) { taskMapper.insert(task); }
    public void update(Task task) { taskMapper.updateById(task); }
    public void delete(Long id) { taskMapper.deleteById(id); }
}
