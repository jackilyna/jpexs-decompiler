/*
 *  Copyright (C) 2010-2023 JPEXS, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.abc;

import java.io.IOException;

/**
 *
 * @author JPEXS
 */
public class ABCOpenException extends IOException {

    public ABCOpenException(String message) {
        super(message);        
    }
    
    public ABCOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
