/*
 * Copyright (C) 2021 Servoy BV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sablo.specification.property;

/**
 * @author jcompager
 * @since 2021.06
 *
 */
public interface ISmartSortOrderPrevalence extends ISmartPropertyValue
{
	/**
	 * How lower the number how higher up (first i the list) it is sorted is when attach is called.
	 * So if 1 smart value gives a 0 back and another a 1 that 0 is call attach on first.
	 */
	int getPrevalence();
}
