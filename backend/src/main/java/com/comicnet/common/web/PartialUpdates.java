package com.comicnet.common.web;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PartialUpdates {
  private final Validator validator;
  private final JsonMapper json = JsonMapper.builder().build();

  public PartialUpdates(Validator validator) {
    this.validator = validator;
  }

  public <T> T merge(T current, JsonNode patch, Class<T> type) {
    if (!patch.isObject() || patch.isEmpty()) throw invalid();
    ObjectNode merged = (ObjectNode) json.valueToTree(current);
    for (var entry : patch.properties()) {
      if (!merged.has(entry.getKey())) throw invalid();
      merged.set(entry.getKey(), entry.getValue());
    }
    try {
      T result = json.treeToValue(merged, type);
      if (!validator.validate(result).isEmpty()) throw invalid();
      return result;
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      throw invalid();
    }
  }

  private DomainException invalid() {
    return new DomainException(400, "INVALID_PATCH", "Trường hoặc giá trị cập nhật không hợp lệ.");
  }
}
