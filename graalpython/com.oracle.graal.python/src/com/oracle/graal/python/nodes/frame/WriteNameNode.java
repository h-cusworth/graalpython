/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class WriteNameNode extends PNodeWithContext implements AccessNameNode {
    protected final TruffleString attributeId;

    protected WriteNameNode(TruffleString attributeId) {
        this.attributeId = attributeId;
    }

    public static WriteNameNode create(TruffleString attributeId) {
        return WriteNameNodeGen.create(attributeId);
    }

    public abstract void execute(VirtualFrame frame, Object value);

    @Specialization(guards = "!hasLocals(frame)")
    protected static void writeGlobal(VirtualFrame frame, Object value,
                    @Cached("create(attributeId)") WriteGlobalNode writeGlobal) {
        writeGlobal.executeObject(frame, value);
    }

    @Specialization(guards = "hasLocalsDict(frame)")
    protected void writeLocalsDict(VirtualFrame frame, Object value,
                    @Bind("this") Node inliningTarget,
                    @Cached HashingCollectionNodes.SetItemNode setItem) {
        PDict frameLocals = (PDict) PArguments.getSpecialArgument(frame);
        setItem.execute(frame, inliningTarget, frameLocals, attributeId, value);
    }

    @Specialization(guards = "hasLocals(frame)")
    protected void writeLocal(VirtualFrame frame, Object value,
                    @Cached PyObjectSetItem setItem) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        setItem.execute(frame, frameLocals, attributeId, value);
    }
}
