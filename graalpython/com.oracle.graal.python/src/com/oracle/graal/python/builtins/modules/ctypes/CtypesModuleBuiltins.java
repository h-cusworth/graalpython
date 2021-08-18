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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ArgError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtr;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins.paramFunc;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCData_GetContainer;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins.PyCFuncPtrFromDllNode.strchr;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.getBytes;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_GLOBAL;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_LOCAL;
import static com.oracle.graal.python.runtime.PosixConstants.RTLD_NOW;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.append;
import static com.oracle.graal.python.util.PythonUtils.newStringBuilder;
import static com.oracle.graal.python.util.PythonUtils.sbToString;

import java.util.List;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.PyLongAsVoidPtr;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.AuditNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheckExact;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectHashNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(defineModule = "_ctypes")
public class CtypesModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CtypesModuleBuiltinsFactory.getFactories();
    }

    protected static final int FUNCFLAG_STDCALL = 0x0;
    protected static final int FUNCFLAG_CDECL = 0x1;
    protected static final int FUNCFLAG_HRESULT = 0x2;
    protected static final int FUNCFLAG_PYTHONAPI = 0x4;
    protected static final int FUNCFLAG_USE_ERRNO = 0x8;
    protected static final int FUNCFLAG_USE_LASTERROR = 0x10;

    protected static final int TYPEFLAG_ISPOINTER = 0x100;
    protected static final int TYPEFLAG_HASPOINTER = 0x200;
    protected static final int TYPEFLAG_HASUNION = 0x400;
    protected static final int TYPEFLAG_HASBITFIELD = 0x800;

    protected static final int DICTFLAG_FINAL = 0x1000;

    private static void addSymbol(PythonModule ctypesModule, String key, String name, DLHandler handle, Python3Core core) {
        try {
            Object sym = InteropLibrary.getUncached().readMember(handle.library, name);
            long adr = InteropLibrary.getUncached().asPointer(sym);
            NativeFunction func = new NativeFunction(sym, adr, name, false);
            registerAddress(core.getContext(), adr, func);
            // PyLong_FromVoidPtr(ptr);
            PythonNativeVoidPtr ptr = core.factory().createNativeVoidPtr(func, adr);
            ctypesModule.setAttribute(key, ptr);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            // Not supported.. carry on
        }
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("_pointer_type_cache", core.factory().createDict());
        builtinConstants.put("FUNCFLAG_CDECL", FUNCFLAG_CDECL);
        builtinConstants.put("FUNCFLAG_USE_ERRNO", FUNCFLAG_USE_ERRNO);
        builtinConstants.put("FUNCFLAG_USE_LASTERROR", FUNCFLAG_USE_LASTERROR);
        builtinConstants.put("FUNCFLAG_PYTHONAPI", FUNCFLAG_PYTHONAPI);
        builtinConstants.put("__version__", "1.1.0");
        builtinConstants.put("CFuncPtr", core.lookupType(PyCFuncPtr));
        builtinConstants.put("ArgumentError", core.lookupType(ArgError));
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule ctypesModule = core.lookupBuiltinModule("_ctypes");
        ctypesModule.setAttribute("_string_at_addr", core.factory().createNativeVoidPtr(StringAtFunction.create()));
        ctypesModule.setAttribute("_cast_addr", core.factory().createNativeVoidPtr(CastFunction.create()));
        ctypesModule.setAttribute("_wstring_at_addr", core.factory().createNativeVoidPtr(WStringAtFunction.create()));
        int rtldLocal = RTLD_LOCAL.getValueIfDefined();
        ctypesModule.setAttribute("RTLD_LOCAL", rtldLocal);
        ctypesModule.setAttribute("RTLD_GLOBAL", RTLD_GLOBAL.getValueIfDefined());

        DLHandler handle = DlOpenNode.loadNFILibrary(core.getContext(), NFIBackend.NATIVE, "", rtldLocal);
        addSymbol(ctypesModule, "_memmove_addr", "memmove", handle, core);
        addSymbol(ctypesModule, "_memset_addr", "memset", handle, core);
    }

    private static final String get_errno = "get_errno";
    private static final String set_errno = "set_errno";
    private static final String POINTER = "POINTER";
    private static final String pointer = "pointer";
    private static final String _unpickle = "_unpickle";
    private static final String buffer_info = "buffer_info";
    private static final String resize = "resize";
    private static final String dlopen = "dlopen";
    private static final String dlclose = "dlclose";
    private static final String dlsym = "dlsym";
    private static final String alignment = "alignment";
    private static final String sizeof = "sizeof";
    private static final String byref = "byref";
    private static final String addressof = "addressof";
    private static final String call_function = "call_function";
    private static final String call_cdeclfunction = "call_cdeclfunction";
    private static final String PyObj_FromPtr = "PyObj_FromPtr";
    private static final String Py_INCREF = "Py_INCREF";
    private static final String Py_DECREF = "Py_DECREF";

    enum NFIBackend {
        NATIVE(""),
        LLVM("with llvm ");

        private final String withClause;

        NFIBackend(String withClause) {
            this.withClause = withClause;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "sym")
    protected static final class NativeFunction implements TruffleObject {
        final Object sym;
        final long adr;
        final String name;

        Object function;
        String signature;

        final boolean isManaged;

        NativeFunction(Object sym, long adr, String name, boolean isManaged) {
            this.sym = sym;
            this.adr = adr;
            this.name = name;
            this.function = null;
            this.signature = null;
            this.isManaged = isManaged;
        }

        protected boolean isManaged() {
            return isManaged;
        }

        protected boolean isManaged(long address) {
            return adr == address;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "library")
    protected static final class DLHandler implements TruffleObject {
        final Object library;
        final String name;
        final long adr;
        final boolean isManaged;
        boolean isClosed;

        private DLHandler(Object library, long adr, String name, boolean isManaged) {
            this.library = library;
            this.adr = adr;
            this.name = name;
            this.isManaged = isManaged;
            this.isClosed = false;
        }

        public Object getLibrary() {
            return library;
        }
    }

    @TruffleBoundary
    protected static void registerAddress(PythonContext context, long adr, Object o) {
        context.getCtypesAdrMap().put(adr, o);
    }

    @TruffleBoundary
    protected static Object getObjectAtAddress(PythonContext context, long adr) {
        return context.getCtypesAdrMap().get(adr);
    }

    public static class CtypesThreadState {
        /*
         * ctypes maintains thread-local storage that has space for two error numbers: private
         * copies of the system 'errno' value and, on Windows, the system error code accessed by the
         * GetLastError() and SetLastError() api functions.
         *
         * Foreign functions created with CDLL(..., use_errno=True), when called, swap the system
         * 'errno' value with the private copy just before the actual function call, and swapped
         * again immediately afterwards. The 'use_errno' parameter defaults to False, in this case
         * 'ctypes_errno' is not touched.
         *
         * On Windows, foreign functions created with CDLL(..., use_last_error=True) or WinDLL(...,
         * use_last_error=True) swap the system LastError value with the ctypes private copy.
         *
         * The values are also swapped immediately before and after ctypes callback functions are
         * called, if the callbacks are constructed using the new optional use_errno parameter set
         * to True: CFUNCTYPE(..., use_errno=TRUE) or WINFUNCTYPE(..., use_errno=True).
         *
         * New ctypes functions are provided to access the ctypes private copies from Python:
         *
         * - ctypes.set_errno(value) and ctypes.set_last_error(value) store 'value' in the private
         * copy and returns the previous value.
         *
         * - ctypes.get_errno() and ctypes.get_last_error() returns the current ctypes private
         * copies value.
         */
        // (mq) TODO: add another field for errno (Windows support)
        int errno; // see '_ctypes_get_errobj'

        NFIBackend backendType;
        EconomicMapStorage ptrtype_cache;
        EconomicMapStorage cache;

        CtypesThreadState() {
            this.ptrtype_cache = EconomicMapStorage.create();
            this.cache = EconomicMapStorage.create();
            this.backendType = NFIBackend.NATIVE;
        }

        static CtypesThreadState get(PythonContext context, PythonLanguage language) {
            CtypesThreadState ctypes = context.getThreadState(language).getCtypes();
            if (ctypes == null) {
                ctypes = new CtypesThreadState();
                context.getThreadState(language).setCtypes(ctypes);
            }
            return ctypes;
        }
    }

    @Builtin(name = get_errno)
    @GenerateNodeFactory
    public abstract static class GetErrnoNode extends PythonBuiltinNode {

        @Specialization
        public Object get_errno(@SuppressWarnings("unused") Object args,
                        @CachedLanguage PythonLanguage language,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.get_errno");
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            return ctypes.errno;
        }
    }

    @Builtin(name = set_errno, minNumOfPositionalArgs = 1, parameterNames = {"errno"})
    @ArgumentClinic(name = "errno", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class SetErrnoNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.SetErrnoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object set_errno(int newErrno,
                        @CachedLanguage PythonLanguage language,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.set_errno", newErrno);
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            int oldErrno = ctypes.errno;
            ctypes.errno = newErrno;
            return oldErrno;
        }
    }

    @Builtin(name = POINTER, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PointerTypeNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object POINTER(VirtualFrame frame, Object cls,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @CachedLanguage PythonLanguage language,
                        @Cached IsTypeNode isTypeNode,
                        @Cached CallNode callNode,
                        @Cached GetNameNode getNameNode,
                        @Cached CastToJavaStringNode toJavaStringNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            Object result = hlib.getItem(ctypes.ptrtype_cache, cls);
            if (result != null) {
                return result;
            }
            Object key;
            if (PGuards.isString(cls)) {
                String name = toJavaStringNode.execute(cls);
                String buf = PythonUtils.format("LP_%s", name);
                Object[] args = new Object[]{buf, PyCPointer, factory().createDict()};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = factory().createNativeVoidPtr((TruffleObject) result); // PyLong_FromVoidPtr(result);
                                                                             // TODO
            } else if (isTypeNode.execute(cls)) {
                String buf = PythonUtils.format("LP_%s", getNameNode.execute(cls));
                PTuple bases = factory().createTuple(new Object[]{PyCPointer});
                Object[] args = new Object[]{buf, bases, factory().createDict(new PKeyword[]{new PKeyword("_type_", cls)})};
                result = callNode.execute(frame, PyCPointerType, args, PKeyword.EMPTY_KEYWORDS);
                key = cls;
            } else {
                throw raise(TypeError, "must be a ctypes type");
            }
            hlib.setItem(ctypes.ptrtype_cache, key, result);
            return result;
        }
    }

    @Builtin(name = pointer, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PointerObjectNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object pointer(VirtualFrame frame, Object arg,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @CachedLanguage PythonLanguage language,
                        @Cached PointerTypeNode callPOINTER,
                        @Cached CallNode callNode,
                        @Cached GetClassNode getClassNode) {
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            Object typ = hlib.getItem(ctypes.ptrtype_cache, getClassNode.execute(arg));
            if (typ != null) {
                return callNode.execute(frame, typ, arg);
            }
            typ = callPOINTER.call(frame, getClassNode.execute(arg));
            return callNode.execute(frame, typ, arg);
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @Builtin(name = _unpickle, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class UnpickleNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object unpickle(VirtualFrame frame, Object typ, PTuple state,
                        @Cached CallNode callNode,
                        @Cached("create(__NEW__)") LookupAndCallUnaryNode lookupAndCallUnaryNode,
                        @Cached("create(__SETSTATE__)") GetAttributeNode setStateAttr) {
            Object obj = lookupAndCallUnaryNode.executeObject(frame, typ);
            Object meth = setStateAttr.executeObject(frame, obj);
            callNode.execute(frame, meth, state);
            return obj;
        }

    }

    @Builtin(name = buffer_info, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BufferInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object buffer_info(Object arg,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict == null) {
                dict = pyObjectStgDictNode.execute(arg);
            }
            if (dict == null) {
                throw raise(TypeError, "not a ctypes type or object");
            }
            Object[] shape = new Object[dict.ndim];
            for (int i = 0; i < dict.ndim; ++i) {
                shape[i] = dict.shape[i];
            }

            return factory().createTuple(new Object[]{dict.format, dict.ndim, factory().createTuple(shape)});
        }
    }

    @Builtin(name = resize, minNumOfPositionalArgs = 2, parameterNames = {"", "size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class ResizeNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.ResizeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public Object resize(CDataObject obj, int size,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyObjectStgDictNode.execute(obj);
            if (dict == null) {
                throw raise(TypeError, "excepted ctypes instance");
            }
            if (size < dict.size) {
                throw raise(ValueError, "minimum size is %d", dict.size);
            }
            if (obj.b_needsfree == 0) {
                throw raise(ValueError, "Memory cannot be resized because this object doesn't own it");
            }
            /*- TODO
            if (size <= sizeof(obj.b_value)) {
                // internal default buffer is large enough
                obj.b_size = size;
                return PNone.NONE;
            }
            Object ptr;
            if (!_CDataObject_HasExternalBuffer(obj)) {
                /*
                 * We are currently using the objects default buffer, but it isn't large enough any
                 * more.
                 * /
                ptr = PyMem_Malloc(size);
                memset(ptr, 0, size);
                memmove(ptr, obj.b_ptr, obj.b_size);
            } else {
                ptr = PyMem_Realloc(obj.b_ptr, size);
            }
            obj.b_ptr.ptr = ptr;
            */
            obj.b_size = size;
            return PNone.NONE;
        }
    }

    @Builtin(name = dlopen, minNumOfPositionalArgs = 1, parameterNames = {"name", "mode"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.String, defaultValue = "\"\"", useDefaultForNone = true)
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "Integer.MIN_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class DlOpenNode extends PythonBinaryClinicBuiltinNode {

        private static final String MACOS_Security_LIB = "/System/Library/Frameworks/Security.framework/Security";
        private static final String MACOS_CoreFoundation_LIB = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation";
        // "LibFFILibrary(" + handle + ")"
        private static final String LIBFFI_ADR_FORMAT_PREFIX = "LibFFILibrary";
        private static final int LIBFFI_ADR_FORMAT_START = LIBFFI_ADR_FORMAT_PREFIX.length() + 1;

        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(DlOpenNode.class);

        protected static String flagsToString(int flag) {
            return (flag & RTLD_LOCAL.getValueIfDefined()) != 0 ? "RTLD_LOCAL|RTLD_NOW" : "RTLD_GLOBAL|RTLD_NOW";
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.DlOpenNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        protected static DLHandler loadNFILibrary(PythonContext context, NFIBackend backendType, String name, int flags) {
            CompilerAsserts.neverPartOfCompilation();
            String src;
            if (PString.length(name) > 0) {
                src = PythonUtils.format("%sload (%s) \"%s\"", backendType.withClause, flagsToString(flags), name);
            } else {
                src = "default";
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("Loading native library %s %s", name, backendType.withClause));
            }
            Source loadSrc = Source.newBuilder("nfi", src, "load:" + name).internal(true).build();
            Object handler = context.getEnv().parseInternal(loadSrc).call();
            String handleStr = (String) InteropLibrary.getUncached().toDisplayString(handler);
            long adr = Long.parseLong(handleStr.substring(LIBFFI_ADR_FORMAT_START, handleStr.length() - 1));
            return new DLHandler(handler, adr, name, false);
        }

        @TruffleBoundary
        protected static Object loadLLVMLibrary(PythonContext context, String path) throws Exception {
            CompilerAsserts.neverPartOfCompilation();
            Source loadSrc = Source.newBuilder(PythonLanguage.LLVM_LANGUAGE, context.getPublicTruffleFileRelaxed(path)).build();
            return context.getEnv().parseInternal(loadSrc).call();
        }

        @TruffleBoundary
        private static String getErrMsg(Exception e) {
            String errmsg = e != null ? e.getMessage() : null;
            if (errmsg == null || PString.length(errmsg) == 0) {
                return "dlopen() error";
            }
            return errmsg;
        }

        @Specialization
        public Object py_dl_open(VirtualFrame frame, String name_str, int m,
                        // @CachedLibrary(limit = "2") PythonObjectLibrary toBuffer,
                        // @Cached DecodeUTF8FSPathNode stringOrBytesToBytesNode,
                        @CachedLanguage PythonLanguage language,
                        @Cached PyObjectHashNode hashNode,
                        @Cached AuditNode auditNode) {
            int mode = m != Integer.MIN_VALUE ? m : RTLD_LOCAL.getValueIfDefined();
            mode |= RTLD_NOW.getValueIfDefined();
            /*-
            String name_str = null;
            if (name != PNone.NONE) {
                try {
                    // PyUnicode_FSConverter(name, &name2)
                    name_str = stringOrBytesToBytesNode.execute(frame, name);
                } catch (PException e) {
                    // pass through
                    // name_str = PyBytes_AS_STRING(name2)
                    try {
                        name_str = createUTF8String(toBuffer.getBufferBytes(name));
                    } catch (UnsupportedMessageException ee) {
                        // pass through
                        name_str = null;
                    }
                }
            }
            */
            auditNode.audit("ctypes.dlopen", name_str);
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            DLHandler handle;
            Exception exception = null;
            try {
                if (PString.endsWith(name_str, getContext().getSoAbi())) {
                    Object handler = loadLLVMLibrary(getContext(), name_str);
                    long adr = hashNode.execute(frame, handler);
                    handle = new DLHandler(handler, adr, name_str, true);
                    registerAddress(getContext(), handle.adr, handle);
                    return factory().createNativeVoidPtr(handle);
                } else {
                    /*-
                     TODO: (mq) cryptography in macos isn't always compatible with ctypes. 
                     */
                    if (!PString.equals(name_str, MACOS_Security_LIB) && !PString.equals(name_str, MACOS_CoreFoundation_LIB)) {
                        handle = loadNFILibrary(getContext(), ctypes.backendType, name_str, mode);
                        registerAddress(getContext(), handle.adr, handle);
                        return factory().createNativeVoidPtr(handle, handle.adr);
                    }
                }
            } catch (Exception e) {
                exception = e;
            }
            throw raise(OSError, getErrMsg(exception));
        }

    }

    static String ctypes_dlerror() { // TODO
        return "dlerror";
    }

    static DLHandler getHandler(Object ptr, PythonContext context) {
        Object adr = ptr;
        if (PGuards.isInteger(adr)) {
            adr = getObjectAtAddress(context, (Long) adr);
        }
        if (adr instanceof DLHandler) {
            return (DLHandler) adr;
        }
        return null;
    }

    static NativeFunction getNativeFunction(Object ptr, PythonContext context) {
        Object adr = ptr;
        if (PGuards.isInteger(adr)) {
            adr = getObjectAtAddress(context, (Long) adr);
        }
        if (adr instanceof NativeFunction) {
            return (NativeFunction) adr;
        }
        return null;
    }

    @Builtin(name = dlclose, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DlCloseNode extends PythonUnaryBuiltinNode {

        private static boolean dlclose(Object ptr, PythonContext context) {
            DLHandler handle = getHandler(ptr, context);
            if (handle != null) {
                handle.isClosed = true;
                return true;
            }
            return false;
        }

        @Specialization
        public Object py_dl_close(Object h,
                        @Cached PyLongAsVoidPtr asVoidPtr) {
            Object handle = asVoidPtr.execute(h);

            if (!dlclose(handle, getContext())) {
                throw raise(OSError, ctypes_dlerror());
            }
            return PNone.NONE;
        }
    }

    protected static DLHandler getHandleFromLongObject(Object obj, PythonContext context,
                    PyLongAsVoidPtr asVoidPtr,
                    PRaiseNode raiseNode) {
        Object h = null;
        try {
            h = asVoidPtr.execute(obj);
        } catch (PException e) {
            // throw later.
        }
        DLHandler handle = h != null ? getHandler(h, context) : null;
        if (handle == null) {
            throw raiseNode.raise(ValueError, "could not convert the _handle attribute to a pointer");
        }
        return handle;
    }

    protected static NativeFunction getFunctionFromLongObject(Object obj, PythonContext context,
                    PyLongAsVoidPtr asVoidPtr) {
        if (obj instanceof NativeFunction) {
            return (NativeFunction) obj;
        }
        Object f = asVoidPtr.execute(obj);
        return f != null ? getNativeFunction(f, context) : null;
    }

    protected abstract static class CtypesDlSymNode extends PNodeWithRaise {

        protected abstract Object execute(VirtualFrame frame, DLHandler handle, Object n, PythonContext context, PythonObjectFactory factory, PythonBuiltinClassType error);

        @Specialization
        protected Object ctypes_dlsym(VirtualFrame frame, DLHandler handle, Object n, PythonContext context, PythonObjectFactory factory, PythonBuiltinClassType error,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaStringNode asString,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            String name = asString.execute(n);
            if (handle == null || handle.isClosed) {
                throw raise(error, ctypes_dlerror());
            }
            try {
                Object sym = ilib.readMember(handle.library, name);
                boolean isManaged = handle.isManaged;
                long adr = isManaged ? hashNode.execute(frame, sym) : ilib.asPointer(sym);
                NativeFunction func = new NativeFunction(sym, adr, name, isManaged);
                registerAddress(context, adr, func);
                // PyLong_FromVoidPtr(ptr);
                if (!isManaged) {
                    return factory.createNativeVoidPtr(func, adr);
                } else {
                    return factory.createNativeVoidPtr(func);
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw raise(error, e.getMessage());
            }
        }
    }

    @Builtin(name = dlsym, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DlSymNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object py_dl_sym(VirtualFrame frame, Object obj, Object name,
                        @Cached PyLongAsVoidPtr asVoidPtr,
                        @Cached AuditNode auditNode,
                        @Cached CtypesDlSymNode dlSymNode) {
            auditNode.audit("ctypes.dlsym/handle", obj, name);
            DLHandler handle = getHandleFromLongObject(obj, getContext(), asVoidPtr, getRaiseNode());
            return dlSymNode.execute(frame, handle, name, getContext(), factory(), OSError);
        }
    }

    @Builtin(name = alignment, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AlignmentNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object align_func(Object obj,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            dict = pyObjectStgDictNode.execute(obj);
            if (dict != null) {
                return dict.align;
            }

            throw raise(TypeError, "no alignment info");
        }
    }

    @Builtin(name = sizeof, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(Object obj,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.execute(obj);
            if (dict != null) {
                return dict.size;
            }

            if (pyTypeCheck.isCDataObject(obj)) {
                return ((CDataObject) obj).b_size;
            }
            throw raise(TypeError, "this type has no size");
        }
    }

    @Builtin(name = byref, minNumOfPositionalArgs = 1, parameterNames = {"", "offset"})
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class ByRefNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CtypesModuleBuiltinsClinicProviders.ByRefNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public Object doit(CDataObject obj, int offset,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {

            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(null, obj, offset);
            }

            PyCArgObject parg = factory().createCArgObject();
            parg.tag = 'P';
            // parg.pffi_type = FFIType.ffi_type_pointer;
            parg.pffi_type = pyObjectStgDictNode.execute(obj).ffi_type_pointer;
            parg.obj = obj;
            parg.value = obj.b_ptr.ref(offset);
            return parg;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object obj, Object off) {
            Object clazz = GetClassNode.getUncached().execute(obj);
            String name = GetNameNode.getUncached().execute(clazz);
            throw raise(TypeError, "byref() argument must be a ctypes instance, not '%s'", name);
        }
    }

    protected static Object getObjectAt(PythonContext context, Object ptr) {
        if (PGuards.isInteger(ptr)) {
            return getObjectAtAddress(context, (Long) ptr);
        }
        return ptr;
    }

    @Builtin(name = call_function, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CallFunctionNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object call_function(VirtualFrame frame, Object f, PTuple arguments,
                        @Cached AuditNode auditNode,
                        @CachedLanguage PythonLanguage language,
                        @Cached CallProcNode callProcNode,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached PyLongAsVoidPtr asVoidPtr) {
            // Object func = _parse_voidp(tuple[0]);
            NativeFunction func = (NativeFunction) getObjectAt(getContext(), asVoidPtr.execute(f));
            auditNode.audit("ctypes.call_function", func, arguments);
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            return callProcNode.execute(frame, func,
                            getArray.execute(arguments.getSequenceStorage()),
                            0, /* flags */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.argtypes */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.converters */
                            null, /* self.restype */
                            null,
                            ctypes,
                            factory(),
                            getContext().getEnv());
        }
    }

    @Builtin(name = addressof, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AddressOfNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(CDataObject obj,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached AuditNode auditNode) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                return error(null, obj);
            }
            auditNode.audit("ctypes.addressof", obj);
            return obj.b_ptr;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object o) {
            throw raise(TypeError, "invalid type");
        }
    }

    @Builtin(name = call_cdeclfunction, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CallCdeclfunctionNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doit(VirtualFrame frame, Object f, PTuple arguments,
                        @Cached AuditNode auditNode,
                        @CachedLanguage PythonLanguage language,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached CallProcNode callProcNode,
                        @Cached PyLongAsVoidPtr asVoidPtr) {
            // Object func = _parse_voidp(tuple[0]);
            NativeFunction func = (NativeFunction) getObjectAt(getContext(), asVoidPtr.execute(f));
            auditNode.audit("ctypes.call_function", func, arguments);
            CtypesThreadState ctypes = CtypesThreadState.get(getContext(), language);
            return callProcNode.execute(frame, func,
                            getArray.execute(arguments.getSequenceStorage()),
                            FUNCFLAG_CDECL, /* flags */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.argtypes */
                            PythonUtils.EMPTY_OBJECT_ARRAY, /* self.converters */
                            null, /* self.restype */
                            null,
                            ctypes,
                            factory(),
                            getContext().getEnv());
        }
    }

    protected static class argument {
        FFIType ffi_type;
        Object keep;
        Object value;
    }

    /**
     * Requirements, must be ensured by the caller: - argtuple is tuple of arguments - argtypes is
     * either NULL, or a tuple of the same size as argtuple
     *
     * - XXX various requirements for restype, not yet collected
     *
     * argtypes is amisleading name: This is a tuple of methods, not types: the .from_param class
     * methods of the types
     */
    @ImportStatic({CastFunction.class, StringAtFunction.class, WStringAtFunction.class})
    protected abstract static class CallProcNode extends PNodeWithRaise {

        abstract Object execute(VirtualFrame frame, NativeFunction pProc, Object[] argtuple, int flags, Object[] argtypes, Object[] converters, Object restype, Object checker,
                        CtypesThreadState state,
                        PythonObjectFactory factory,
                        Env env);

        /*
         * bpo-13097: Max number of arguments _ctypes_callproc will accept.
         *
         * This limit is enforced for the `alloca()` call in `_ctypes_callproc`, to avoid allocating
         * a massive buffer on the stack.
         */
        protected static final int CTYPES_MAX_ARGCOUNT = 1024;

        @Specialization(guards = "!pProc.isManaged()")
        Object _ctypes_callproc(VirtualFrame frame,
                        NativeFunction pProc,
                        Object[] argarray,
                        @SuppressWarnings("unused") int flags,
                        Object[] argtypes, Object[] converters,
                        Object restype,
                        Object checker,
                        @SuppressWarnings("unused") CtypesThreadState state,
                        PythonObjectFactory factory,
                        Env env,
                        @Cached ConvParamNode convParamNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CallNode callNode,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            int n, argcount;
            n = argcount = argarray.length;

            if (argcount > CTYPES_MAX_ARGCOUNT) {
                throw raise(ArgError, "too many arguments (%d), maximum is %d", argcount, CTYPES_MAX_ARGCOUNT);
            }

            argument[] args = new argument[argcount];
            int argtype_count = argtypes != null ? argtypes.length : 0;

            /* Convert the arguments */
            for (int i = 0; i < n; ++i) {
                args[i] = new argument();
                Object arg = argarray[i]; /* borrowed ref */
                /*
                 * For cdecl functions, we allow more actual arguments than the length of the
                 * argtypes tuple. This is checked in _ctypes::PyCFuncPtr_Call
                 */
                if (converters != null && argtype_count > i) {
                    Object converter = converters[i];
                    Object tp = argtypes[i];
                    Object v;
                    try {
                        v = callNode.execute(frame, converter, tp, arg);
                    } catch (PException e) {
                        throw raise(ArgError, "argument %d: ", i + 1);
                    }

                    convParamNode.execute(frame, v, i + 1, args[i], factory, env);
                } else {
                    convParamNode.execute(frame, arg, i + 1, args[i], factory, env);
                }
            }

            FFIType rtype = _ctypes_get_ffi_type(restype, pyTypeStgDictNode);

            Object[] avalues = new Object[argcount];
            FFIType[] atypes = new FFIType[argcount];
            for (int i = 0; i < argcount; ++i) {
                atypes[i] = args[i].ffi_type;
                // if (atypes[i].type == FFI_TYPE_STRUCT)
                // avalues[i] = args[i].value;
                // else
                avalues[i] = args[i].value;
            }

            Object result = callNativeFunction(pProc, avalues, atypes, rtype, ilib);
            if (rtype == FFIType.nfi_type_string) {
                try {
                    result = ilib.asString(result);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }

            if (!PGuards.isPNone(checker)) {
                return callNode.execute(checker, result);
            }

            return result;
            /*- TODO (mq) require more support from NFI.
            Object resbuf = alloca(max(rtype.size, sizeof(ffi_arg)));
            _call_function_pointer(flags, pProc, avalues, atypes, rtype, resbuf, argcount, state);
            return GetResult(restype, resbuf, checker);
            */
        }

        @Specialization(guards = "pProc.isManaged()")
        Object doManaged(NativeFunction pProc,
                        Object[] argarray,
                        @SuppressWarnings("unused") int flags,
                        @SuppressWarnings("unused") Object[] argtypes,
                        @SuppressWarnings("unused") Object[] converters,
                        @SuppressWarnings("unused") Object restype,
                        @SuppressWarnings("unused") Object checker,
                        @SuppressWarnings("unused") CtypesThreadState state,
                        @SuppressWarnings("unused") PythonObjectFactory factory,
                        @SuppressWarnings("unused") Env env,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            try {
                return ilib.execute(pProc.sym, argarray);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raise(RuntimeError, "ffi_call failed");
            }
        }

        @TruffleBoundary
        protected static Object getFunction(Object symbol, String signature) throws Exception {
            return InteropLibrary.getUncached(symbol).invokeMember(symbol, "bind", signature);
        }

        /**
         * NFI compatible native function calls (temporary replacement)
         */
        Object callNativeFunction(NativeFunction pProc, Object[] avalues, FFIType[] atypes, FFIType restype,
                        InteropLibrary ilib) {
            if (pProc.function == null) {
                String signature = FFIType.buildNFISignature(atypes, restype);
                Object function;
                try {
                    function = getFunction(pProc.sym, signature);
                } catch (Exception e) {
                    throw raise(RuntimeError, "ffi_prep_cif failed");
                }
                pProc.function = function;
                pProc.signature = signature;
            } else {
                assert PString.equals(pProc.signature, FFIType.buildNFISignature(atypes, restype));
            }
            try {
                return ilib.execute(pProc.function, avalues);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raise(RuntimeError, "ffi_call failed");
            }
        }

        /*
         * libffi uses:
         *
         * ffi_status ffi_prep_cif(ffi_cif *cif, ffi_abi abi, unsigned int nargs, ffi_type *rtype,
         * ffi_type **atypes);
         *
         * and then
         *
         * void ffi_call(ffi_cif *cif, void *fn, void *rvalue, void **avalues);
         */
        /*- TODO (mq) require more support from NFI.
        void _call_function_pointer(int flags,
                                    NativeFunction pProc,
                                    Object[] avalues,
                                    FFIType[] atypes,
                                    FFIType restype,
                                    Object resmem,
                                    int argcount,
                                    CtypesThreadState state) {
                // XXX check before here
                if (restype == null) {
                    throw raise(RuntimeError, "No ffi_type for result");
                }
        
                int cc = FFI_DEFAULT_ABI;
                ffi_cif cif;
                if (FFI_OK != ffi_prep_cif(&cif, cc, argcount, restype, atypes)) {
                    throw raise(RuntimeError, "ffi_prep_cif failed");
                }
        
                Object error_object = null;
                if ((flags & (FUNCFLAG_USE_ERRNO | FUNCFLAG_USE_LASTERROR)) != 0) {
                    error_object = state.errno;
                }
                if ((flags & FUNCFLAG_PYTHONAPI) == 0)
                    Py_UNBLOCK_THREADS
                if ((flags & FUNCFLAG_USE_ERRNO) != 0) {
                    int temp = state.errno;
                    state.errno = errno;
                    errno = temp;
                }
                ffi_call(&cif, pProc, resmem, avalues);
                if ((flags & FUNCFLAG_USE_ERRNO) != 0) {
                    int temp = state.errno;
                    state.errno = errno;
                    errno = temp;
                }
                if ((flags & FUNCFLAG_PYTHONAPI) == 0)
                    Py_BLOCK_THREADS
                if ((flags & FUNCFLAG_PYTHONAPI) && PyErr_Occurred()) {
                }
        }
        */

        /*
         * Convert the C value in result into a Python object, depending on restype.
         *
         * - If restype is NULL, return a Python integer. - If restype is None, return None. - If
         * restype is a simple ctypes type (c_int, c_void_p), call the type's getfunc, pass the
         * result to checker and return the result. - If restype is another ctypes type, return an
         * instance of that. - Otherwise, call restype and return the result.
         */
        /*- TODO
        static Object GetResult(FFIType restype, PtrValue result, Object checker,
                                CallNode callNode,
                                GetFuncNode getFuncNode,
                                PyTypeStgDictNode pyTypeStgDictNode,
                                PythonObjectFactory factory,
                                PRaiseNode raiseNode) {
                if (restype == null)
                    return result;
                
                StgDictObject dict = pyTypeStgDictNode.execute(restype);
                if (dict == null)
                    // return PyObject_CallFunction(restype, "i", *(int *)result);
                    return result;
        
                Object retval;
                if (dict.getfunc != FieldGet.nil && !_ctypes_simple_instance(restype)) {
                    retval = getFuncNode.execute(dict.getfunc, result, dict.size);
                } else {
                    retval = PyCData_FromBaseObj(restype, null, 0, result, factory, raiseNode, pyTypeStgDictNode);
                }
                
                if (checker == null || retval == null)
                    return retval;
        
                return callNode.execute(checker, retval);
        }
        */

    }

    /*
     * Convert a single Python object into a PyCArgObject and return it.
     */
    protected abstract static class ConvParamNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, Object obj, int index, argument pa, PythonObjectFactory factory, Env env);

        @Specialization
        void ConvParam(VirtualFrame frame, Object obj, int index, argument pa, PythonObjectFactory factory, Env env,
                        @Cached PyTypeCheckExact pyTypeCheckExact,
                        @Cached PyLongCheckNode longCheckNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached PyNumberAsSizeNode asInt,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttr,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            pa.keep = null; /* so we cannot forget it later */

            StgDictObject dict = pyObjectStgDictNode.execute(obj);
            if (dict != null) {
                assert dict.paramfunc != -1;
                /* If it has an stgdict, it is a CDataObject */
                PyCArgObject carg = paramFunc(dict.paramfunc, (CDataObject) obj, dict, factory);
                pa.ffi_type = carg.pffi_type;
                // memcpy(&pa.value, &carg.value, sizeof(pa.value)); TODO
                assert carg.value.offset == 0 : "TODO";
                pa.value = carg.value.ptr.getNativeObject(context.getCtypesSupport());
                pa.keep = carg;
                return;
            }

            if (pyTypeCheckExact.isPyCArg(obj)) {
                PyCArgObject carg = (PyCArgObject) obj;
                pa.ffi_type = carg.pffi_type;
                pa.keep = obj;
                assert carg.value.offset == 0 : "TODO";
                // memcpy(&pa.value, &carg.value, sizeof(pa.value)); TODO
                pa.value = carg.value.ptr.getNativeObject(context.getCtypesSupport());
                return;
            }

            /* check for None, integer, string or unicode and use directly if successful */
            if (obj == PNone.NONE) {
                pa.ffi_type = FFIType.ffi_type_pointer;
                // pa.value = null;
                pa.value = env.asGuestValue(null); // TODO check
                // pa.value = GetNativeNullNodeGen.getUncached().execute(); // TODO check
                return;
            }

            if (longCheckNode.execute(obj)) {
                pa.ffi_type = FFIType.ffi_type_sint;
                try {
                    pa.value = asInt.executeExact(frame, obj);
                } catch (PException e) {
                    // e.expectOverflowError(); TODO
                    throw raise(OverflowError, "int too long to convert");
                }
                return;
            }

            if (lib.isBuffer(obj)) {
                // pa.ffi_type = FFIType.ffi_type_pointer;
                pa.ffi_type = FFIType.ffi_type_sint8_array;
                pa.value = env.asGuestValue(getBytes(lib, obj)); // PyBytes_AsString(obj);
                pa.keep = obj;
                return;
            }

            /*-
            if (PyUnicode_Check(obj)) { // CTYPES_UNICODE
                pa.ffi_type = ffi_type_pointer;
                pa.value = PyUnicode_AsWideCharString(obj, NULL);
                pa.keep = PyCapsule_New(pa.value.p, CTYPES_CAPSULE_NAME_PYMEM, pymem_destructor);
                return 0;
            }
            
             */

            Object arg = lookupAttr.execute(obj, CDataTypeBuiltins._as_parameter_);

            /*
             * Which types should we exactly allow here? integers are required for using Python
             * classes as parameters (they have to expose the '_as_parameter_' attribute)
             */
            if (arg != null) {
                ConvParam(frame, arg, index, pa, factory, env, pyTypeCheckExact, longCheckNode, context, lib, asInt, lookupAttr, pyObjectStgDictNode);
                return;
            }
            throw raise(TypeError, "Don't know how to convert parameter %d", index);
        }
    }

    @Builtin(name = PyObj_FromPtr, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyObjFromPtrNode extends PythonUnaryBuiltinNode {

        static Object converter(Object obj) { // , Object[] address) TODO
            // *address = PyLong_AsVoidPtr(obj);
            // return *address != NULL;
            return obj;
        }

        @Specialization
        public Object doit(Object obj,
                        @Cached AuditNode auditNode) {
            Object ob = converter(obj);
            auditNode.audit("ctypes.PyObj_FromPtr", "(O)", ob);
            return ob;
        }
    }

    @Builtin(name = Py_INCREF, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyINCREFNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(Object arg,
                        @Cached AddRefCntNode incRefCntNode) {
            incRefCntNode.execute(arg, 1 /* that's what this function is for */ + //
                            1 /* that for returning it */);
            return arg;
        }
    }

    @Builtin(name = Py_DECREF, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDECREFNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(Object arg,
                        @Cached SubRefCntNode decRefCntNode,
                        @Cached AddRefCntNode incRefCntNode) {
            decRefCntNode.execute(arg, 1 /* that's what this function is for */);
            incRefCntNode.execute(arg, 1 /* that for returning it */);
            return arg;
        }
    }

    @GenerateUncached
    protected abstract static class CastFunctionNode extends Node {

        abstract Object execute(Object ptr, Object src, Object ctype);

        @Specialization
        static Object cast(CDataObject ptr, Object src, Object ctype,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PythonObjectFactory factory,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            cast_check_pointertype(ctype, raiseNode, pyTypeCheck, pyTypeStgDictNode, isTypeNode, getClassNode, getNameNode);
            CDataObject result = (CDataObject) callNode.execute(ctype);

            /*
             * The casted objects '_objects' member:
             *
             * It must certainly contain the source objects one. It must contain the source object
             * itself.
             */
            if (pyTypeCheck.isCDataObject(src)) {
                CDataObject obj = (CDataObject) src;
                /*
                 * PyCData_GetContainer will initialize src.b_objects, we need this so it can be
                 * shared
                 */
                PyCData_GetContainer(obj, factory);

                /* But we need a dictionary! */
                if (obj.b_objects == null) {
                    obj.b_objects = factory.createDict();
                }
                result.b_objects = obj.b_objects;
                if (PGuards.isDict(result.b_objects)) {
                    // PyLong_FromVoidPtr((void *)src);
                    PDict dict = (PDict) result.b_objects;
                    Object index = factory.createNativeVoidPtr((TruffleObject) src);
                    hlib.setItem(dict.getDictStorage(), index, src);
                }
            }
            /* Should we assert that result is a pointer type? */
            // memcpy(result.b_ptr, &ptr, sizeof(void *));
            result.b_ptr = ptr.b_ptr.ref(0);
            return result;
        }

        static void cast_check_pointertype(Object arg,
                        PRaiseNode raiseNode,
                        PyTypeCheck pyTypeCheck,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        IsTypeNode isTypeNode,
                        GetClassNode getClassNode,
                        GetNameNode getNameNode) {
            if (pyTypeCheck.isPyCPointerTypeObject(arg)) {
                return;
            }
            if (pyTypeCheck.isPyCFuncPtrTypeObject(arg)) {
                return;
            }
            StgDictObject dict = pyTypeStgDictNode.execute(arg);
            if (dict != null && dict.proto != null) {
                if (PGuards.isString(dict.proto) && (strchr("sPzUZXO", PString.charAt((String) dict.proto, 0)))) {
                    /* simple pointer types, c_void_p, c_wchar_p, BSTR, ... */
                    return;
                }
            }
            Object clazz = isTypeNode.execute(arg) ? arg : getClassNode.execute(arg);
            throw raiseNode.raise(TypeError, "cast() argument 2 must be a pointer type, not %s", getNameNode.execute(clazz));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class CastFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new CastFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), "cast", true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached CastFunctionNode castFunctionNode) {
            return castFunctionNode.execute(arguments[0], arguments[1], arguments[2]);
        }
    }

    @GenerateUncached
    protected abstract static class StringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static Object string_at(String ptr, int size,
                        @Cached AuditNode auditNode) {
            auditNode.audit("ctypes.string_at", ptr, size);
            if (size == -1) {
                return ptr; // PyBytes_FromStringAndSize(ptr, strlen(ptr));
            }
            return PString.substring(ptr, 0, size);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class StringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new StringAtFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), "string_at", true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached StringAtFunctionNode stringAtFunctionNode) {
            return stringAtFunctionNode.execute(arguments[0], arguments[1]);
        }

    }

    @GenerateUncached
    protected abstract static class WStringAtFunctionNode extends Node {

        abstract Object execute(Object ptr, Object size);

        @Specialization
        static Object wstring_at(String ptr, int size,
                        @Cached AuditNode auditNode) {
            int ssize = size;
            auditNode.audit("ctypes.wstring_at", ptr, ssize);
            if (ssize == -1) {
                ssize = PString.length(ptr); // wcslen(ptr);
            }
            return PString.substring(ptr, 0, ssize); // PyUnicode_FromWideChar(ptr, ssize);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class WStringAtFunction implements TruffleObject {

        protected static NativeFunction create() {
            Object f = new WStringAtFunction();
            return new NativeFunction(f, PyObjectHashNodeGen.getUncached().execute(null, f), "wstring_at", true);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Cached WStringAtFunctionNode wStringAtFunctionNode) {
            return wStringAtFunctionNode.execute(arguments[0], arguments[1]);
        }
    }

    static FFIType _ctypes_get_ffi_type(Object obj,
                    PyTypeStgDictNode pyTypeStgDictNode) {
        if (obj == null) {
            return FFIType.ffi_type_sint;
        }
        StgDictObject dict = pyTypeStgDictNode.execute(obj);
        if (dict == null) {
            return FFIType.ffi_type_sint;
        }
        return dict.ffi_type_pointer;
    }

    /*
     * Allocate a memory block for a pep3118 format string, adding the given prefix (if non-null),
     * an additional shape prefix, and a suffix. Returns NULL on failure, with the error indicator
     * set. If called with a suffix of NULL the error indicator must already be set.
     */
    static String _ctypes_alloc_format_string_with_shape(int ndim, int[] shape, String prefix, String suffix) {
        StringBuilder buf = newStringBuilder();
        if (prefix != null) {
            append(buf, prefix);
        }
        if (ndim > 0) {
            /* Add the prefix "(shape[0],shape[1],...,shape[ndim-1])" */
            append(buf, "(");
            String fmt = "%d,";
            for (int k = 0; k < ndim; ++k) {
                if (k == ndim - 1) {
                    fmt = "%d)";
                }
                append(buf, PythonUtils.format(fmt, shape[k]));
            }
        }
        if (suffix != null) {
            append(buf, suffix);
        }
        return sbToString(buf);
    }
}
