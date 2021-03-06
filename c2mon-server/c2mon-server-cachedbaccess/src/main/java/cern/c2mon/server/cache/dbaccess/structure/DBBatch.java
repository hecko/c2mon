/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.server.cache.dbaccess.structure;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Specifies a batch of records that needs loading.
 * All cache objects between the specified rows are
 * loaded.
 *
 * @author Mark Brightwell, Franz Ritter
 *
 */
@Data
@AllArgsConstructor
public class DBBatch {

  /**
   * First record to load (in an assumed ordered list of records
   * that need loading from the DB) (inclusive).
   */
  private Long startRow;

  /**
   * Last cache object to load (inclusive).
   */
  private Long endRow;

  /**
   * Get the number of rows between startRow and endRow (inclusive).
   *
   * @return number of rows
   */
  public long getRowCount() {
    return endRow - startRow + 1;
  }

}
