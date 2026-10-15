package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.service.ClassService;
import com.tfzhang.linuxback.vo.ClassVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassMapper classMapper;

    @Override
    public List<ClassVO> listClasses() {
        LambdaQueryWrapper<TeachingClass> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachingClass::getTeacherId, CurrentUser.requireId())
                .orderByAsc(TeachingClass::getName, TeachingClass::getId);
        return classMapper.selectList(wrapper).stream()
                .map(this::toClassVO)
                .toList();
    }

    private ClassVO toClassVO(TeachingClass teachingClass) {
        ClassVO vo = new ClassVO();
        vo.setId(teachingClass.getId());
        vo.setName(teachingClass.getName());
        vo.setSemester(teachingClass.getSemester());
        vo.setCreateTime(teachingClass.getCreateTime());
        return vo;
    }
}
