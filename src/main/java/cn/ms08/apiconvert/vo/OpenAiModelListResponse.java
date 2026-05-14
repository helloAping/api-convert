package cn.ms08.apiconvert.vo;

import java.util.List;

public record OpenAiModelListResponse(
        String object,
        List<Model> data
) {
    public record Model(
            String id,
            String object,
            String ownedBy
    ) {
    }
}
