package cn.ms08.apiconvert.vo;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    long total,
    int page,
    int pageSize
) {}
