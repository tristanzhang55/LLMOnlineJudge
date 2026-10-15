package com.tfzhang.linuxback.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.entity.User;
import org.apache.ibatis.builder.MapperBuilderAssistant;

public final class MybatisPlusLambda {

    private MybatisPlusLambda() {
    }

    public static void initCommonEntities() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.getDbConfig().setTableUnderline(false);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Experiment.class);
        TableInfoHelper.initTableInfo(assistant, ExperimentQuestion.class);
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        TableInfoHelper.initTableInfo(assistant, Dispute.class);
        TableInfoHelper.initTableInfo(assistant, Question.class);
        TableInfoHelper.initTableInfo(assistant, SubQuestion.class);
        TableInfoHelper.initTableInfo(assistant, SubQuestionScore.class);
        TableInfoHelper.initTableInfo(assistant, TeachingClass.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
    }
}
