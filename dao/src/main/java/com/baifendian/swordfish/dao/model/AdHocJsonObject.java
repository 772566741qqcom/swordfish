/*
 * Copyright (C) 2017 Baifendian Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baifendian.swordfish.dao.model;

import java.util.ArrayList;
import java.util.List;

public class AdHocJsonObject {
  /**
   * 返回的表头
   */
  private List<String> titles;

  /**
   * 返回的数据
   */
  private List<List<String>> values = new ArrayList<>();

  /**
   * getter method
   *
   * @return the titles
   * @see AdHocJsonObject#titles
   */
  public List<String> getTitles() {
    return titles;
  }

  /**
   * setter method
   *
   * @param titles the titles to set
   * @see AdHocJsonObject#titles
   */
  public void setTitles(List<String> titles) {
    this.titles = titles;
  }

  /**
   * getter method
   *
   * @return the values
   * @see AdHocJsonObject#values
   */
  public List<List<String>> getValues() {
    return values;
  }

  /**
   * setter method
   *
   * @param values the values to set
   * @see AdHocJsonObject#values
   */
  public void setValues(List<List<String>> values) {
    this.values = values;
  }
}
