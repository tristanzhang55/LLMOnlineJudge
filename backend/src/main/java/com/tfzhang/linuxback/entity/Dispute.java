package com.tfzhang.linuxback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dispute")
public class Dispute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;
    private String disputeReason;
    private LocalDateTime disputeTime;
    private String manualComment;
    private LocalDateTime resolveTime;
    private LocalDateTime withdrawnTime;
    private LocalDateTime replyReadTime;

    @TableLogic
    private Integer isDelete;
}
