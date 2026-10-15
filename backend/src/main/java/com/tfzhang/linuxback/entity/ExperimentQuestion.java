package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_question")
public class ExperimentQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long experimentId;
    private Long questionId;
    private Integer points;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
