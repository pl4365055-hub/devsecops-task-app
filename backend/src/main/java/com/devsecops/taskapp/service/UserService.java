package com.devsecops.taskapp.service;

import com.devsecops.taskapp.entity.User;
import com.devsecops.taskapp.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public List<User> listAll() {
        return userMapper.selectList(null);
    }

    public boolean delete(Long id) {
        return userMapper.deleteById(id) > 0;
    }
}
