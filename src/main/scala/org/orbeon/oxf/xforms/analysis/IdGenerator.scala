/**
 * Copyright (C) 2014 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import scala.collection.mutable

// IdGenerator which uses a BitSet.
//
// The working assumption is that automatic ids will typically be allocated in a continuous way. Using a regular Set
// to hold this information is a waste. We also assume that there are at most a few thousand ids. Over that, ids are
// placed in a Set.
class IdGenerator(var _lastId: Int = 1) {

    import IdGenerator._

    private val bits   = mutable.BitSet()
    private val others = mutable.Set[String]()

    private def containsStandardId(id: Int): Boolean =
        bits(id - 1) || others.contains(AutomaticIdPrefix + id)

    def ids = (bits.iterator map (i ⇒ AutomaticIdPrefix + (i + 1).toString)) ++ others.iterator

    def isDuplicate(id: String): Boolean =  id match {
        case AutomaticIdFormat(digits) ⇒
            bits contains (digits.toInt - 1)
        case _ ⇒
            others contains id
    }
    
    def add(id: String): Unit = id match {
        case AutomaticIdFormat(digits) if digits.toInt <= MaxBits ⇒
            bits += digits.toInt - 1
        case _ ⇒
            others += id
    }
    
    // Skip existing ids to handle these cases:
    //
    // - user uses attribute of the form xf-*
    // - XBL copies id attributes from bound element, so within template the id may be of the form xf-*
    def nextId(): String = {

        while (containsStandardId(_lastId))
            _lastId += 1

        val result = AutomaticIdPrefix + _lastId
        _lastId += 1
        result
    }
    
    def lastId = _lastId
}

private object IdGenerator {
    val MaxBytes = 1024         // 1024 means up to xf-8096
    val MaxBits  = MaxBytes * 8

    val AutomaticIdPrefix = "xf-"
    val AutomaticIdFormat = "xf-(\\d+)".r
}