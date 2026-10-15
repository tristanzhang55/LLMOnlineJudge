package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.ClassVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    private static final long TEACHER_ID = 1L;

    @Mock
    private ClassMapper classMapper;

    @InjectMocks
    private ClassServiceImpl classService;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void listClasses_returnsMappedVosForDevTeacher() {
        when(classMapper.selectList(any())).thenReturn(List.of(
                teachingClass(2L, "23软工1班", "2026春"),
                teachingClass(1L, "22计科3班", "2026春")));

        List<ClassVO> result = classService.listClasses();

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals("23软工1班", result.get(0).getName());
        assertEquals("2026春", result.get(0).getSemester());
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 0), result.get(0).getCreateTime());
        assertEquals(1L, result.get(1).getId());
        assertEquals("22计科3班", result.get(1).getName());
        assertEquals(LocalDateTime.of(2026, 8, 1, 8, 0), result.get(1).getCreateTime());
    }

    @Test
    void listClasses_filtersByDevTeacherIdAndOrdersByNameThenId() {
        when(classMapper.selectList(any())).thenReturn(List.of());

        classService.listClasses();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<TeachingClass>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(classMapper).selectList(captor.capture());

        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("TEACHERID"));
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("NAME"));
        assertTrue(sql.contains("ID"));
        assertFalse(sql.contains("CREATETIME"));
    }

    @Test
    void listClasses_mapsNullSemester() {
        when(classMapper.selectList(any())).thenReturn(List.of(
                teachingClass(3L, "24信安2班", null)));

        List<ClassVO> result = classService.listClasses();

        assertEquals(1, result.size());
        assertNull(result.get(0).getSemester());
    }

    @Test
    void listClasses_returnsEmptyListWhenNoRows() {
        when(classMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<ClassVO> result = classService.listClasses();

        assertTrue(result.isEmpty());
    }

    private static TeachingClass teachingClass(long id, String name, String semester) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setName(name);
        teachingClass.setTeacherId(TEACHER_ID);
        teachingClass.setSemester(semester);
        teachingClass.setCreateTime(LocalDateTime.of(2026, id == 2L ? 9 : 8, 1, 8, 0));
        teachingClass.setIsDelete(0);
        return teachingClass;
    }
}
