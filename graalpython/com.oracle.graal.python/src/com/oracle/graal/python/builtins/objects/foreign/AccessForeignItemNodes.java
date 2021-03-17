/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.GetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.RemoveForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.foreign.AccessForeignItemNodesFactory.SetForeignItemNodeGen;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfRangeNode;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.CoerceToIntSlice;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.ComputeIndices;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

abstract class AccessForeignItemNodes {

    @ImportStatic(PythonOptions.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    protected abstract static class AccessForeignItemBaseNode extends PNodeWithContext {
        @Child PRaiseNode raiseNode;

        protected PException raise(PythonBuiltinClassType type, String msg, Object... arguments) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode.raise(type, msg, arguments);
        }

        protected static boolean isSlice(Object o) {
            return o instanceof PSlice;
        }

        protected static boolean isString(Object o) {
            return o instanceof String || o instanceof PString;
        }

        protected int getForeignSize(Object object, InteropLibrary libForObject) {
            long foreignSizeObj = 0;
            try {
                foreignSizeObj = libForObject.getArraySize(object);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (foreignSizeObj <= Integer.MAX_VALUE) {
                return (int) foreignSizeObj;
            }
            throw raise(TypeError, ErrorMessages.NUMBER_S_CANNOT_FIT_INTO_INDEXSIZED_INT, foreignSizeObj);
        }

        protected SliceInfo materializeSlice(PSlice idxSlice, Object object, ComputeIndices compute, InteropLibrary libForObject) {
            int foreignSize = getForeignSize(object, libForObject);
            return compute.execute(null, idxSlice, foreignSize);
        }
    }

    protected abstract static class GetForeignItemNode extends AccessForeignItemBaseNode {
        @Child private PForeignToPTypeNode toPythonNode;

        public abstract Object execute(VirtualFrame frame, Object object, Object idx);

        @Specialization(guards = "lib.hasArrayElements(object)")
        Object doArraySlice(Object object, PSlice idxSlice,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib,
                        @Cached PythonObjectFactory factory,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute,
                        @Cached LenOfRangeNode sliceLen) {
            SliceInfo mslice;
            mslice = materializeSlice(sliceCast.execute(idxSlice), object, compute, lib);
            Object[] values = new Object[sliceLen.len(mslice)];
            for (int i = mslice.start, j = 0; i < mslice.stop; i += mslice.step, j++) {
                values[j] = readForeignIndex(object, i, lib);
            }
            return factory.createList(values);
        }

        @Specialization(guards = {"lib.hasArrayElements(object)", "!isPSlice(key)"})
        Object doArrayIndex(Object object, Object key,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isNumber(key) && lib.fitsInInt(key)) {
                try {
                    return readForeignIndex(object, lib.asInt(key), lib);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, object, key);
            }
        }

        @Specialization(guards = {"lib.hasHashEntries(object)"})
        Object doHashKey(Object object, Object key,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isHashEntryReadable(object, key)) {
                try {
                    return lib.readHashValue(object, key);
                } catch (UnsupportedMessageException | UnknownKeyException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw raise(KeyError, "%s", lib.toDisplayString(key, true));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFail(Object object, Object key) {
            throw raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, object);
        }

        private Object readForeignIndex(Object object, long index, InteropLibrary libForObject) {
            if (libForObject.isArrayElementReadable(object, index)) {
                try {
                    return getToPythonNode().executeConvert(libForObject.readArrayElement(object, index));
                } catch (UnsupportedMessageException | InvalidArrayIndexException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            throw raise(IndexError, ErrorMessages.INVALID_INDEX_S, index);
        }

        private PForeignToPTypeNode getToPythonNode() {
            if (toPythonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toPythonNode = insert(PForeignToPTypeNode.create());
            }
            return toPythonNode;
        }

        public static GetForeignItemNode create() {
            return GetForeignItemNodeGen.create();
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    protected abstract static class SetForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(VirtualFrame frame, Object object, Object idx, Object value);

        @Specialization(guards = "lib.hasArrayElements(object)")
        public Object doArraySlice(VirtualFrame frame, Object object, PSlice idxSlice, Object pvalues,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary pvaluesLib,
                        @Cached GetNextNode getNext,
                        @Shared("unsupportedType") @Cached BranchProfile unsupportedType,
                        @Shared("wrongIndex") @Cached BranchProfile wrongIndex,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute) {
            Object value;
            SliceInfo mslice;
            mslice = materializeSlice(sliceCast.execute(idxSlice), object, compute, lib);
            Object iter = pvaluesLib.getIteratorWithFrame(pvalues, frame);
            for (int i = mslice.start; i < mslice.stop; i += mslice.step) {
                value = getNext.execute(frame, iter);
                writeForeignIndex(object, i, value, lib, unsupportedType, wrongIndex);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"lib.hasArrayElements(object)", "!isPSlice(key)"})
        Object doArrayIndex(Object object, Object key, Object value,
                        @Cached BranchProfile unsupportedMessage,
                        @Shared("unsupportedType") @Cached BranchProfile unsupportedType,
                        @Shared("wrongIndex") @Cached BranchProfile wrongIndex,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isNumber(key) && lib.fitsInInt(key)) {
                try {
                    writeForeignIndex(object, lib.asInt(key), value, lib, unsupportedType, wrongIndex);
                    return PNone.NONE;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                unsupportedMessage.enter();
                throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, object, key);
            }
        }

        @Specialization(guards = {"lib.hasHashEntries(object)"})
        Object doHashKey(Object object, Object key, Object value,
                        @Shared("wrongIndex") @Cached BranchProfile wrongIndex,
                        @Shared("unsupportedType") @Cached BranchProfile unsupportedType,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isHashEntryWritable(object, key)) {
                try {
                    lib.writeHashEntry(object, key, value);
                    return PNone.NONE;
                } catch (UnsupportedMessageException | UnknownKeyException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (UnsupportedTypeException e) {
                    unsupportedType.enter();
                    throw raise(TypeError, ErrorMessages.TYPE_P_NOT_SUPPORTED_BY_FOREIGN_OBJ, value);
                }
            }
            wrongIndex.enter();
            throw raise(KeyError, "%s", lib.toDisplayString(key, true));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFail(Object object, Object key, Object value) {
            throw raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, object);
        }

        private void writeForeignIndex(Object object, int idx, Object value, InteropLibrary libForObject, BranchProfile unsupportedType, BranchProfile wrongIndex) {
            if (libForObject.isArrayElementWritable(object, idx)) {
                try {
                    libForObject.writeArrayElement(object, idx, value);
                    return;
                } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (UnsupportedTypeException e) {
                    unsupportedType.enter();
                    throw raise(TypeError, ErrorMessages.TYPE_P_NOT_SUPPORTED_BY_FOREIGN_OBJ, value);
                }
            }
            wrongIndex.enter();
            throw raise(IndexError, ErrorMessages.INVALID_INDEX_S, idx);
        }

        public static SetForeignItemNode create() {
            return SetForeignItemNodeGen.create();
        }
    }

    protected abstract static class RemoveForeignItemNode extends AccessForeignItemBaseNode {

        public abstract Object execute(VirtualFrame frame, Object object, Object idx);

        @Specialization(guards = "lib.hasArrayElements(object)")
        Object doArraySlice(Object object, PSlice idxSlice,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib,
                        @Cached CoerceToIntSlice sliceCast,
                        @Cached ComputeIndices compute) {
            SliceInfo mslice = materializeSlice(sliceCast.execute(idxSlice), object, compute, lib);
            for (int i = mslice.start; i < mslice.stop; i += mslice.step) {
                removeForeignValue(object, i, lib);
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"lib.hasArrayElements(object)", "!isPSlice(key)"})
        Object doArrayIndex(Object object, Object key,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isNumber(key) && lib.fitsInInt(key)) {
                try {
                    removeForeignValue(object, lib.asInt(key), lib);
                    return PNone.NONE;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, object, key);
            }
        }

        @Specialization(guards = {"lib.hasHashEntries(object)"})
        Object doHashKey(Object object, Object key,
                        @Shared("lib") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary lib) {
            if (lib.isHashEntryRemovable(object, key)) {
                try {
                    lib.removeHashEntry(object, key);
                    return PNone.NONE;
                } catch (UnsupportedMessageException | UnknownKeyException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw raise(KeyError, "%s", lib.toDisplayString(key, true));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFail(Object object, Object key) {
            throw raise(TypeError, ErrorMessages.OBJ_DOESNT_SUPPORT_DELETION, object);
        }

        private void removeForeignValue(Object object, int idx, InteropLibrary libForObject) {
            if (libForObject.isArrayElementRemovable(object, idx)) {
                try {
                    libForObject.removeArrayElement(object, idx);
                    return;
                } catch (InvalidArrayIndexException ex) {
                    throw raise(IndexError, ErrorMessages.INVALID_INDEX_S, idx);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            throw raise(IndexError, ErrorMessages.INVALID_INDEX_S, idx);
        }

        public static RemoveForeignItemNode create() {
            return RemoveForeignItemNodeGen.create();
        }
    }
}
