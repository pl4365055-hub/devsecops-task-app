package com.devsecops.taskapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devsecops.taskapp.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
