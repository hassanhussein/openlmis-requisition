/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.requisition.utils;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import org.junit.Test;
import org.springframework.data.domain.Sort;
import java.io.IOException;

public class CustomSortDeserializerTest {

  private ObjectMapper mapper = new ObjectMapper();

  @Test
  public void shouldDeserializeArraySort() throws IOException {
    Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "startDate"),
        new Sort.Order(Sort.Direction.DESC, "endDate"));
    TestObject testObject = new TestObject(sort);

    Sort newSort = deserialize(mapper.writeValueAsString(testObject));
    assertEquals(sort, newSort);
  }

  private Sort deserialize(String json) throws IOException {
    TestObject testObject = mapper.readValue(json, TestObject.class);
    return testObject.getSort();
  }

  @AllArgsConstructor
  private static class TestObject {

    private Sort sort;

    public Sort getSort() {
      return sort;
    }

    @JsonDeserialize(using = CustomSortDeserializer.class)
    public void setSort(Sort sort) {
      this.sort = sort;
    }
  }
}