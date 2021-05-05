/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of CPython's {@code PyFloat_AsDouble}. Converts the argument to a Java {@code double}
 * using its {@code __float__} special method. If not available, falls back to {@code __index__}
 * special method. Otherwise, raises {@code TypeError}. Can raise {@code OverflowError} when using
 * {@code __index__} and the returned integer wouldn't fit into double.
 */
@GenerateUncached
@ImportStatic({PGuards.class, PythonBuiltinClassType.class})
public abstract class PyFloatAsDoubleNode extends PNodeWithContext {
    public abstract double execute(Frame frame, Object object);

    @Specialization
    double doDouble(double object) {
        return object;
    }

    @Specialization
    double doPFloat(PFloat object) {
        return object.getValue();
    }

    @Specialization
    double doInt(int object) {
        return object;
    }

    @Specialization
    double doLong(long object) {
        return object;
    }

    @Specialization
    double doBoolean(boolean object) {
        return object ? 1.0 : 0.0;
    }

    @Specialization(guards = {"!isDouble(object)", "!isInteger(object)", "!isBoolean(object)", "!isPFloat(object)", "!isNativeObject(object) || !isSubtypeNode.execute(type, PFloat)"}, limit = "1")
    double doObject(VirtualFrame frame, Object object,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                    @Bind("getClassNode.execute(object)") Object type,
                    @Cached LookupSpecialMethodNode.Dynamic lookup,
                    @Cached CallUnaryMethodNode call,
                    @Cached GetClassNode resultClassNode,
                    @Cached IsBuiltinClassProfile resultProfile,
                    @Cached IsSubtypeNode resultSubtypeNode,
                    @Cached PyIndexCheckNode indexCheckNode,
                    @Cached PyNumberIndexNode indexNode,
                    @Cached CastToJavaDoubleNode cast,
                    @Cached WarningsModuleBuiltins.WarnNode warnNode,
                    @Cached PRaiseNode raiseNode) {
        Object floatDescr = lookup.execute(frame, type, object, __FLOAT__, false);
        if (floatDescr != PNone.NO_VALUE) {
            Object result = call.executeObject(frame, floatDescr, object);
            Object resultType = resultClassNode.execute(result);
            if (!resultProfile.profileClass(resultType, PythonBuiltinClassType.PFloat)) {
                if (!resultSubtypeNode.execute(resultType, PythonBuiltinClassType.PFloat)) {
                    throw raiseNode.raise(TypeError, ErrorMessages.RETURNED_NON_FLOAT, object, result);
                } else {
                    warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                                    ErrorMessages.WARN_P_RETURNED_NON_P, object, __FLOAT__, "float", result, "float");
                }
            }
            return cast.execute(result);
        }
        if (indexCheckNode.execute(object)) {
            Object index = indexNode.execute(frame, object);
            return cast.execute(index);
        }
        throw raiseNode.raise(TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, object);
    }

    @Specialization(guards = "isSubtypeNode.execute(getClassNode.execute(object), PFloat)", limit = "1")
    double doNativeObject(@SuppressWarnings("unused") PythonAbstractNativeObject object,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                    @Cached CastToJavaDoubleNode cast) {
        return cast.execute(object);
    }

    public static PyFloatAsDoubleNode create() {
        return PyFloatAsDoubleNodeGen.create();
    }

    public static PyFloatAsDoubleNode getUncached() {
        return PyFloatAsDoubleNodeGen.getUncached();
    }
}
