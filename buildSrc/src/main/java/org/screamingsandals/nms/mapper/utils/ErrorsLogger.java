/*
 * Copyright 2022 ScreamingSandals
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.screamingsandals.nms.mapper.utils;

import lombok.Data;

@Data
public class ErrorsLogger {
    private int errors;
    private boolean silent;

    public void log(String error) {
        errors++;
        if (!silent) {
            System.out.println(error);
        }
    }

    public void reset() {
        errors = 0;
        silent = false;
    }

    public void printWarn() {
        if (errors > 0) {
            System.out.println(errors + " symbols (fields, methods) not found, but they are not excluded.");
        }
    }
}
