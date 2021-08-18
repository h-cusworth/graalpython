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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.CField;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SIZEOF__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.LookupAttributeOnTypeNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.CheckIsSequenceNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.StgDict)
public class StgDictBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StgDictBuiltinsFactory.getFactories();
    }

    protected static final String _anonymous_ = "_anonymous_";

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {

        @Specialization
        Object init(VirtualFrame frame, StgDictObject self, Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached CallNode callNode) {
            Object initMethod = lib.lookupAttribute(PythonBuiltinClassType.PDict, frame, SpecialMethodNames.__INIT__);
            Object[] dictArgs;
            if (args.length > 0) {
                dictArgs = new Object[args.length + 1];
                dictArgs[0] = self;
                PythonUtils.arraycopy(args, 0, dictArgs, 1, args.length);
            } else {
                dictArgs = new Object[]{self};
            }
            callNode.execute(frame, initMethod, dictArgs, kwargs);
            self.format = null;
            self.ndim = 0;
            self.shape = null;
            return PNone.NONE;
        }
    }

    @Builtin(name = __SIZEOF__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doit(VirtualFrame frame, StgDictObject self,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached ObjectBuiltins.SizeOfNode sizeOfNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            long size = asSizeNode.executeLossy(frame, sizeOfNode.call(frame, lib.getDict(self)));
            // size += sizeof(StgDictObject) - sizeof(PyDictObject);
            if (self.format != null) {
                size += PString.length(self.format) + 1;
            }
            size += self.ndim * Integer.BYTES;
            if (self.ffi_type_pointer.elements != null) {
                size += self.ffi_type_pointer.elements.length * FFIType.typeSize();
            }
            return size;
        }
    }

    @ImportStatic(StructUnionTypeBuiltins.class)
    protected abstract static class MakeFieldsNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, Object type, CFieldObject descr, int index, int offset, PythonObjectFactory factory);

        /*
         * descr is the descriptor for a field marked as anonymous. Get all the _fields_ descriptors
         * from descr.proto, create new descriptors with offset and index adjusted, and stuff them
         * into type.
         */
        @Specialization
        void MakeFields(VirtualFrame frame, Object type, CFieldObject descr, int index, int offset, PythonObjectFactory factory,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetClassNode getClassNode,
                        @Cached GetAnyAttributeNode getAttributeNode,
                        @Cached SetAttributeNode.Dynamic setAttributeNode,
                        @Cached CheckIsSequenceNode isSequenceNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GetItemNode getItemNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached("create(_fields_)") GetAttributeNode getAttrString) {
            Object fields = getAttrString.executeObject(frame, descr.proto);
            boolean isFieldsSeq = false;
            try {
                isFieldsSeq = isSequenceNode.execute(fields);
            } catch (PException e) {
                // pass through
            }
            if (!isFieldsSeq) {
                throw raise(TypeError, "_fields_ must be a sequence");
            }

            for (int i = 0; i < sizeNode.execute(frame, fields); ++i) {
                PTuple pair = (PTuple) getItemNode.execute(frame, fields, i); /* borrowed */
                /* Convert to PyArg_UnpackTuple... */
                // PyArg_ParseTuple(pair, "OO|O", & fname, &ftype, &bits);
                Object[] array = getArray.execute(pair.getSequenceStorage());
                Object fname = array[0];
                CFieldObject fdescr = (CFieldObject) getAttributeNode.executeObject(frame, descr.proto, fname);
                if (getClassNode.execute(fdescr) != context.getCore().lookupType(CField)) {
                    throw raise(TypeError, "unexpected type");
                }
                if (fdescr.anonymous != 0) {
                    MakeFields(frame, type, fdescr, index + fdescr.index, offset + fdescr.offset, factory,
                                    context, getClassNode, getAttributeNode, setAttributeNode,
                                    isSequenceNode, sizeNode, getItemNode, getArray, getAttrString);
                    continue;
                }
                CFieldObject new_descr = factory.createCFieldObject(CField);
                // assert (Py_TYPE(new_descr) == PythonBuiltinClassType.CField);
                new_descr.size = fdescr.size;
                new_descr.offset = fdescr.offset + offset;
                new_descr.index = fdescr.index + index;
                new_descr.proto = fdescr.proto;
                new_descr.getfunc = fdescr.getfunc;
                new_descr.setfunc = fdescr.setfunc;

                setAttributeNode.execute(frame, type, fname, new_descr);
            }
        }

    }

    @GenerateUncached
    protected abstract static class PyTypeStgDictNode extends Node {

        abstract StgDictObject execute(Object type);

        protected StgDictObject checkAbstractClass(Object type, PRaiseNode raiseNode) {
            StgDictObject dict = execute(type);
            if (dict == null) {
                throw raiseNode.raise(TypeError, "abstract class");
            }
            return dict;
        }

        /* May return NULL, but does not set an exception! */
        @Specialization
        static StgDictObject PyType_stgdict(Object obj,
                        @Cached IsTypeNode isTypeNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            if (!isTypeNode.execute(obj)) {
                return null;
            }
            PDict dict = lib.getDict(obj);
            if (!PGuards.isStgDict(dict)) {
                return null;
            }
            return (StgDictObject) dict;
        }
    }

    /*
     * This function should be as fast as possible, so we don't call PyType_stgdict above but inline
     * the code, and avoid the PyType_Check().
     */
    @GenerateUncached
    protected abstract static class PyObjectStgDictNode extends Node {

        abstract StgDictObject execute(Object type);

        /* May return null, but does not raise an exception! */
        @Specialization
        static StgDictObject PyObject_stgdict(Object self,
                        @Cached GetClassNode getType,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            Object type = getType.execute(self);
            PDict dict = lib.getDict(type);
            if (!PGuards.isStgDict(dict)) {
                return null;
            }
            return (StgDictObject) dict;
        }
    }

    protected abstract static class MakeAnonFieldsNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, Object type, PythonObjectFactory factory);

        /*
         * Iterate over the names in the type's _anonymous_ attribute, if present,
         */
        @Specialization
        void MakeAnonFields(VirtualFrame frame, Object type, PythonObjectFactory factory,
                        @Cached CheckIsSequenceNode isSequenceNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GetItemNode getItemNode,
                        @Cached MakeFieldsNode makeFieldsNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetAnyAttributeNode getAttr,
                        @Cached LookupAttributeOnTypeNode lookupAnon) {
            Object anon = lookupAnon.execute(type, _anonymous_, false);
            if (PGuards.isPNone(anon)) {
                return;
            }
            boolean isAnonSeq = false;
            try {
                isAnonSeq = isSequenceNode.execute(anon);
            } catch (PException e) {
                // pass through
            }
            if (!isAnonSeq) {
                throw raise(TypeError, "_anonymous_ must be a sequence");
            }

            for (int i = 0; i < sizeNode.execute(frame, anon); ++i) {
                Object fname = getItemNode.execute(frame, anon, i); /* borrowed */
                CFieldObject descr = (CFieldObject) getAttr.executeObject(frame, type, fname);
                if (getClassNode.execute(descr) != CField) {
                    throw raise(AttributeError, "'%U' is specified in _anonymous_ but not in _fields_", fname);
                }
                descr.anonymous = 1;

                /* descr is in the field descriptor. */
                makeFieldsNode.execute(frame, type, descr, descr.index, descr.offset, factory);
            }
        }
    }
}
