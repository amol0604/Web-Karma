/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.karma.rep;

/**
 * @author szekely
 * 
 */
public abstract class CellValue {

	private static CellValue emptyValue = new StringCellValue("");

	public static CellValue getEmptyValue() {
		return emptyValue;
	}

	public boolean isEmptyValue() {
		return this == emptyValue;
	}

	// Return the value as a string.
	public abstract String asString();

	@Override
	public boolean equals(Object o) {
		if (o instanceof CellValue) {
			CellValue cv = (CellValue) o;
			return cv.asString().equals(this.asString());
		}
		return false;
	}
}
