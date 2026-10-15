package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment")
public class Experiment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String description;
    private Long classId;
    private Integer status;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime deadlineTime;
    private LocalDateTime lastPublishedTime;

    @TableLogic
    private Integer isDelete;
}
