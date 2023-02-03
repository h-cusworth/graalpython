/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <Python.h>

#define EXCLUDE_POLYGLOT_API
#define Py_BUILD_CORE
#include "capi.h"

#include <frameobject.h>
#include <pycore_pymem.h>
#include <pycore_moduleobject.h>

#include <stdio.h>
#include <stdint.h>
#include <time.h>

#define MUST_INLINE __attribute__((always_inline)) inline

//#undef NDEBUG


#ifdef NDEBUG
#define LOG(FORMAT, ...)
#define LOGV(FORMAT, ...)
#define LOGS(FORMAT)
#else
#define LOG(FORMAT, ...) printf("\e[32m%s:%d " FORMAT "\e[0m\n", __FUNCTION__, __LINE__, __VA_ARGS__);
#define LOGV(FORMAT, ...) printf("\e[32m%s:%d %s " FORMAT "\e[0m\n", __FUNCTION__, __LINE__, #__VA_ARGS__, __VA_ARGS__);
#define LOGS(FORMAT) printf("\e[32m%s:%d " FORMAT "\e[0m\n", __FUNCTION__, __LINE__);
#endif

//#define STATS

#ifdef STATS
long t() {
    struct timespec spec;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &spec);
    return spec.tv_sec * 1000000000L + spec.tv_nsec;
}

typedef struct CAPIStats {
    struct CAPIStats* next;
    const char* name;
    long count;
    long time;
} CAPIStats;

long totalCount = 0;
long totalTime = 0;

CAPIStats* getStatsList();

void printAllStats() {
    CAPIStats* current = getStatsList();
    printf("\e[35m");
    printf("  %30s - count: %7li / %7lims\n", "TOTAL", totalCount, totalTime / 1000000);
    printf("  ===========================================================\n");
    long percentDelta = totalTime / 200;
    while (current != NULL) {
        if (current->count > 0 && current->time > (totalTime / 1000))
        printf("  %40s - count: %7li / %7lims  (%li%%)\n", current->name, current->count, current->time / 1000000, (current->time * 100 + percentDelta) / totalTime);
        current = current->next;
    }
    printf("\e[0m\n");
}


#endif

#define PY_TYPE_OBJECTS(OBJECT) \
OBJECT(PyAsyncGen_Type, async_generator) \
OBJECT(PyBaseObject_Type, object) \
OBJECT(PyBool_Type, bool) \
OBJECT(PyBuffer_Type, buffer) \
OBJECT(PyByteArrayIter_Type, unimplemented) \
OBJECT(PyByteArray_Type, bytearray) \
OBJECT(PyBytesIter_Type, unimplemented) \
OBJECT(PyBytes_Type, bytes) \
OBJECT(PyCFunction_Type, builtin_function_or_method) \
OBJECT(PyCallIter_Type, unimplemented) \
OBJECT(PyCapsule_Type, capsule) \
OBJECT(PyCell_Type, cell) \
OBJECT(PyClassMethodDescr_Type, unimplemented) \
OBJECT(PyClassMethod_Type, unimplemented) \
OBJECT(PyCmpWrapper_Type, unimplemented) \
OBJECT(PyCode_Type, code) \
OBJECT(PyComplex_Type, complex) \
OBJECT(PyContextToken_Type, unimplemented) \
OBJECT(PyContextVar_Type, unimplemented) \
OBJECT(PyContext_Type, unimplemented) \
OBJECT(PyCoro_Type, unimplemented) \
OBJECT(PyDictItems_Type, unimplemented) \
OBJECT(PyDictIterItem_Type, unimplemented) \
OBJECT(PyDictIterKey_Type, unimplemented) \
OBJECT(PyDictIterValue_Type, unimplemented) \
OBJECT(PyDictKeys_Type, unimplemented) \
OBJECT(PyDictProxy_Type, mappingproxy) \
OBJECT(PyDictRevIterItem_Type, unimplemented) \
OBJECT(PyDictRevIterKey_Type, unimplemented) \
OBJECT(PyDictRevIterValue_Type, unimplemented) \
OBJECT(PyDictValues_Type, unimplemented) \
OBJECT(PyDict_Type, dict) \
OBJECT(PyEllipsis_Type, ellipsis) \
OBJECT(PyEnum_Type, unimplemented) \
OBJECT(PyFilter_Type, unimplemented) \
OBJECT(PyFloat_Type, float) \
OBJECT(PyFrame_Type, frame) \
OBJECT(PyFrozenSet_Type, frozenset) \
OBJECT(PyFunction_Type, function) \
OBJECT(PyGen_Type, generator) \
OBJECT(PyGetSetDescr_Type, getset_descriptor) \
OBJECT(PyInstanceMethod_Type, instancemethod) \
OBJECT(PyListIter_Type, unimplemented) \
OBJECT(PyListRevIter_Type, unimplemented) \
OBJECT(PyList_Type, list) \
OBJECT(PyLongRangeIter_Type, unimplemented) \
OBJECT(PyLong_Type, int) \
OBJECT(PyMap_Type, map) \
OBJECT(PyMemberDescr_Type, member_descriptor) \
OBJECT(PyMemoryView_Type, memoryview) \
OBJECT(PyMethodDescr_Type, method_descriptor) \
OBJECT(PyMethod_Type, method) \
OBJECT(PyModuleDef_Type, moduledef) \
OBJECT(PyModule_Type, module) \
OBJECT(PyNullImporter_Type, unimplemented) \
OBJECT(PyODictItems_Type, unimplemented) \
OBJECT(PyODictIter_Type, unimplemented) \
OBJECT(PyODictKeys_Type, unimplemented) \
OBJECT(PyODictValues_Type, unimplemented) \
OBJECT(PyODict_Type, unimplemented) \
OBJECT(PyPickleBuffer_Type, unimplemented) \
OBJECT(PyProperty_Type, property) \
OBJECT(PyRangeIter_Type, unimplemented) \
OBJECT(PyRange_Type, range) \
OBJECT(PyReversed_Type, unimplemented) \
OBJECT(PySTEntry_Type, unimplemented) \
OBJECT(PySeqIter_Type, unimplemented) \
OBJECT(PySetIter_Type, unimplemented) \
OBJECT(PySet_Type, set) \
OBJECT(PySlice_Type, slice) \
OBJECT(PySortWrapper_Type, unimplemented) \
OBJECT(PyStaticMethod_Type, unimplemented) \
OBJECT(PyStdPrinter_Type, unimplemented) \
OBJECT(PySuper_Type, super) \
OBJECT(PyTraceBack_Type, traceback) \
OBJECT(PyTupleIter_Type, unimplemented) \
OBJECT(PyTuple_Type, tuple) \
OBJECT(PyType_Type, type) \
OBJECT(PyUnicodeIter_Type, unimplemented) \
OBJECT(PyUnicode_Type, str) \
OBJECT(PyWrapperDescr_Type, wrapper_descriptor) \
OBJECT(PyZip_Type, zip) \
OBJECT(_PyAIterWrapper_Type, unimplemented) \
OBJECT(_PyAsyncGenASend_Type, unimplemented) \
OBJECT(_PyAsyncGenAThrow_Type, unimplemented) \
OBJECT(_PyAsyncGenWrappedValue_Type, unimplemented) \
OBJECT(_PyCoroWrapper_Type, unimplemented) \
OBJECT(_PyHamtItems_Type, unimplemented) \
OBJECT(_PyHamtKeys_Type, unimplemented) \
OBJECT(_PyHamtValues_Type, unimplemented) \
OBJECT(_PyHamt_ArrayNode_Type, unimplemented) \
OBJECT(_PyHamt_BitmapNode_Type, unimplemented) \
OBJECT(_PyHamt_CollisionNode_Type, unimplemented) \
OBJECT(_PyHamt_Type, unimplemented) \
OBJECT(_PyInterpreterID_Type, unimplemented) \
OBJECT(_PyManagedBuffer_Type, unimplemented) \
OBJECT(_PyMethodWrapper_Type, unimplemented) \
OBJECT(_PyNamespace_Type, unimplemented) \
OBJECT(_PyNone_Type, NoneType) \
OBJECT(_PyNotImplemented_Type, NotImplementedType) \
OBJECT(_PyWeakref_CallableProxyType, unimplemented) \
OBJECT(_PyWeakref_ProxyType, unimplemented) \
OBJECT(_PyWeakref_RefType, ReferenceType) \
OBJECT(_PyBytesIOBuffer_Type, _BytesIOBuffer) \

#define TYPE_OBJECTS \
TYPE_OBJECT(PyTypeObject*, PyCapsule_Type, capsule, _object) \

#define CONSTANTS \
CONSTANT(struct _longobject*, _Py_FalseStructReference, Py_False) \
CONSTANT(struct _longobject*, _Py_TrueStructReference, Py_True) \
CONSTANT(PyObject*, _Py_EllipsisObjectReference, Py_Ellipsis) \
CONSTANT(PyObject*, _Py_NoneStructReference, Py_None) \
CONSTANT(PyObject*, _Py_NotImplementedStructReference, Py_NotImplemented) \
CONSTANT(PyObject*, _PyTruffle_Zero, _PyTruffle_Zero) \
CONSTANT(PyObject*, _PyTruffle_One, _PyTruffle_One) \
CONSTANT(PyObject*, _PyLong_Zero, PyLong_Zero) \
CONSTANT(PyObject*, _PyLong_One, PyLong_One) \

#define CONSTANT_COPIES \
CONSTANT_ARRAY(const unsigned char, _Py_ascii_whitespace, 256) \
CONSTANT_ARRAY(const unsigned int, _Py_ctype_table, 256) \
CONSTANT_ARRAY(const unsigned char, _Py_ctype_tolower, 256) \
CONSTANT_ARRAY(const unsigned char, _Py_ctype_toupper, 256) \
CONSTANT(struct _PyTraceMalloc_Config, _Py_tracemalloc_config) \
CONSTANT(_Py_HashSecret_t, _Py_HashSecret) \
CONSTANT(int, Py_DebugFlag) \
CONSTANT(int, Py_VerboseFlag) \
CONSTANT(int, Py_QuietFlag) \
CONSTANT(int, Py_InteractiveFlag) \
CONSTANT(int, Py_InspectFlag) \
CONSTANT(int, Py_OptimizeFlag) \
CONSTANT(int, Py_NoSiteFlag) \
CONSTANT(int, Py_BytesWarningFlag) \
CONSTANT(int, Py_FrozenFlag) \
CONSTANT(int, Py_IgnoreEnvironmentFlag) \
CONSTANT(int, Py_DontWriteBytecodeFlag) \
CONSTANT(int, Py_NoUserSiteDirectory) \
CONSTANT(int, Py_UnbufferedStdioFlag) \
CONSTANT(int, Py_HashRandomizationFlag) \
CONSTANT(int, Py_IsolatedFlag) \

#define EXCEPTIONS \
EXCEPTION(ArithmeticError) \
EXCEPTION(AssertionError) \
EXCEPTION(AttributeError) \
EXCEPTION(BaseException) \
EXCEPTION(BlockingIOError) \
EXCEPTION(BrokenPipeError) \
EXCEPTION(BufferError) \
EXCEPTION(BytesWarning) \
EXCEPTION(ChildProcessError) \
EXCEPTION(ConnectionAbortedError) \
EXCEPTION(ConnectionError) \
EXCEPTION(ConnectionRefusedError) \
EXCEPTION(ConnectionResetError) \
EXCEPTION(DeprecationWarning) \
EXCEPTION(EncodingWarning) \
EXCEPTION(EnvironmentError) \
EXCEPTION(EOFError) \
EXCEPTION(Exception) \
EXCEPTION(FileExistsError) \
EXCEPTION(FileNotFoundError) \
EXCEPTION(FloatingPointError) \
EXCEPTION(FutureWarning) \
EXCEPTION(GeneratorExit) \
EXCEPTION(ImportError) \
EXCEPTION(ImportWarning) \
EXCEPTION(IndentationError) \
EXCEPTION(IndexError) \
EXCEPTION(InterruptedError) \
EXCEPTION(IOError) \
EXCEPTION(IsADirectoryError) \
EXCEPTION(KeyboardInterrupt) \
EXCEPTION(KeyError) \
EXCEPTION(LookupError) \
EXCEPTION(MemoryError) \
EXCEPTION(ModuleNotFoundError) \
EXCEPTION(NameError) \
EXCEPTION(NotADirectoryError) \
EXCEPTION(NotImplementedError) \
EXCEPTION(OSError) \
EXCEPTION(OverflowError) \
EXCEPTION(PendingDeprecationWarning) \
EXCEPTION(PermissionError) \
EXCEPTION(ProcessLookupError) \
EXCEPTION(RecursionError) \
EXCEPTION(ReferenceError) \
EXCEPTION(ResourceWarning) \
EXCEPTION(RuntimeError) \
EXCEPTION(RuntimeWarning) \
EXCEPTION(StopAsyncIteration) \
EXCEPTION(StopIteration) \
EXCEPTION(SyntaxError) \
EXCEPTION(SyntaxWarning) \
EXCEPTION(SystemError) \
EXCEPTION(SystemExit) \
EXCEPTION(TabError) \
EXCEPTION(TimeoutError) \
EXCEPTION(TypeError) \
EXCEPTION(UnboundLocalError) \
EXCEPTION(UnicodeDecodeError) \
EXCEPTION(UnicodeEncodeError) \
EXCEPTION(UnicodeError) \
EXCEPTION(UnicodeTranslateError) \
EXCEPTION(UnicodeWarning) \
EXCEPTION(UserWarning) \
EXCEPTION(ValueError) \
EXCEPTION(Warning) \
EXCEPTION(ZeroDivisionError) \


#define DEFINE_TYPE_OBJECT(NAME, TYPENAME) PyTypeObject NAME;
PY_TYPE_OBJECTS(DEFINE_TYPE_OBJECT)
#undef DEFINE_TYPE_OBJECT

#define TYPE_OBJECT(CTYPE, NAME, TYPENAME, STRUCT_TYPE) CTYPE NAME##Reference;
TYPE_OBJECTS
#undef TYPE_OBJECT

#define CONSTANT(TYPE, NAME, INTERNAL_NAME) TYPE NAME;
CONSTANTS
#undef CONSTANT

#define CONSTANT(TYPE, NAME) TYPE NAME;
#define CONSTANT_ARRAY(TYPE, NAME, SIZE) TYPE NAME [SIZE];
CONSTANT_COPIES
#undef CONSTANT
#undef CONSTANT_ARRAY

#define EXCEPTION(NAME) PyObject* PyExc_##NAME;
EXCEPTIONS
#undef EXCEPTION


#define BUILTIN(NAME, RET, ...) RET (*Graal##NAME)(__VA_ARGS__);
CAPI_BUILTINS
#undef BUILTIN


uint32_t Py_Truffle_Options;

void initializeCAPIForwards(void* (*getAPI)(const char*));

PyAPI_FUNC(void) initNativeForward(void* (*getAPI)(const char*), void* (*getType)(const char*), void (*setTypeStore)(const char*, void*)) {
    LOG("%s", "capi_jni.c:initJNIForward\n");
    clock_t t;
    t = clock();

#define SET_TYPE_OBJECT_STORE(NAME, TYPENAME) setTypeStore(#TYPENAME, (void*) &NAME);
    PY_TYPE_OBJECTS(SET_TYPE_OBJECT_STORE)
#undef SET_TYPE_OBJECT_STORE

#define TYPE_OBJECT(CTYPE, NAME, TYPENAME, STRUCT_TYPE) NAME##Reference = (CTYPE) getType(#TYPENAME);
    TYPE_OBJECTS
#undef TYPE_OBJECT

#define CONSTANT(TYPE, NAME, INTERNAL_NAME) NAME = (TYPE) getType(#INTERNAL_NAME);
    CONSTANTS
#undef CONSTANT
    
#define CONSTANT(TYPE, NAME) memcpy((void*) &NAME, getType(#NAME), sizeof(NAME));
#define CONSTANT_ARRAY(TYPE, NAME, SIZE) memcpy((void*) NAME, getType(#NAME), sizeof(NAME));
    CONSTANT_COPIES
#undef CONSTANT
#undef CONSTANT_ARRAY

#define EXCEPTION(NAME) PyExc_##NAME = (PyObject*) getType(#NAME);
    EXCEPTIONS
#undef EXCEPTION

    // now force all classes toNative:
#define SET_TYPE_OBJECT_STORE(NAME, TYPENAME) \
    if (strcmp(#TYPENAME, "unimplemented") != 0) { \
        LOG("initializing toNative: %s %s %lx", #NAME, #TYPENAME, (unsigned long) (void*) &NAME); \
        getType(#TYPENAME); \
    }
    PY_TYPE_OBJECTS(SET_TYPE_OBJECT_STORE)
#undef SET_TYPE_OBJECT_STORE
    
#define BUILTIN(NAME, RET, ...) Graal##NAME = (RET(*)(__VA_ARGS__)) getAPI(#NAME);
CAPI_BUILTINS
#undef BUILTIN
    Py_Truffle_Options = GraalPyTruffle_Native_Options();
    initializeCAPIForwards(getAPI);

#ifdef TIMING
    long total = 0;
    for (int i = 0; i < 1000; i++) {
        long t1 = t();
        long t2 = t();
        total += t2-t1;
    }
    printf("TIME: ns per query = %li\n", total / 1000);
#endif // TIMING
    PyTruffle_Log(PY_TRUFFLE_LOG_FINE, "initNativeForward: %fs", ((double) (clock() - t)) / CLOCKS_PER_SEC);
}

/* Private types are defined here because we need to declare the type cast. */

typedef struct mmap_object mmap_object;

#include "capi_forwards.h"

#define IS_HANDLE(x) ((((intptr_t) (x)) & 0x8000000000000000L) != 0)

inline void assertHandleOrPointer(PyObject* o) {
#ifndef NDEBUG
    if (IS_HANDLE(o)) {
        if ((((intptr_t) o) & 0x7FFFFFFF00000000L) != 0) {
            printf("suspiciously large handle: %lx\n", (unsigned long) o);
        }
    } else {
        if ((((intptr_t) o) & 0x7FFFFFFFFF000000L) == 0) {
            printf("suspiciously small address: %lx\n", (unsigned long) o);
        }
    }
#endif
}
/*
PyAPI_FUNC(struct _typeobject*) _Py_TYPE(PyObject* a) {
    assertHandleOrPointer(a);
    return IS_HANDLE(a) ? _Py_TYPE_Inlined(a) : a->ob_type;
}

PyAPI_FUNC(Py_ssize_t) _Py_SIZE(PyVarObject* a) {
    assertHandleOrPointer((PyObject*) a);
    return IS_HANDLE(a) ? _Py_SIZE_Inlined(a) : a->ob_size;
}

PyAPI_FUNC(void) _Py_SET_TYPE(PyObject* a, struct _typeobject* b) {
    assertHandleOrPointer(a);
    assertHandleOrPointer((PyObject*) b);
    if (IS_HANDLE(a)) {
        _Py_SET_TYPE_Inlined(a, b);
    } else {
        a->ob_type = b;
    }
}
*/

/*
#undef _Py_Dealloc

void Py_DecRef(PyObject *a) {
    LOG("0x%lx", (unsigned long) a)
    if (IS_HANDLE(a)) {
        Py_DecRef_Inlined(a);
    } else {
        Py_ssize_t refcnt = --a->Py_HIDE_IMPL_FIELD(ob_refcnt);
        if (refcnt != 0) {
            // TODO: check for negative refcnt
        } else {
            _Py_Dealloc(a);
        }
    }
}

void Py_IncRef(PyObject *a) {
    LOG("0x%lx", (unsigned long) a)
    if (IS_HANDLE(a)) {
        Py_IncRef_Inlined(a);
    } else {
        a->Py_HIDE_IMPL_FIELD(ob_refcnt)++;
    }
}
*/


PyObject* PyTuple_Pack(Py_ssize_t n, ...) {
    va_list vargs;
    va_start(vargs, n);
    PyObject *result = PyTuple_New(n);
    if (result == NULL) {
        goto end;
    }
    for (int i = 0; i < n; i++) {
        PyObject *o = va_arg(vargs, PyObject *);
        Py_XINCREF(o);
        PyTuple_SetItem(result, i, o);
    }
 end:
    va_end(vargs);
    return result;
}

int (*PyOS_InputHook)(void) = NULL;

static PyObject* callBuffer[1024];
PyAPI_FUNC(PyObject *) PyObject_CallFunctionObjArgs(PyObject *callable, ...) {
    va_list myargs;
    va_start(myargs, callable);
    int count = 0;
    while (count <= 1024) {
        PyObject *o = va_arg(myargs, PyObject *);
        if (o == NULL) {
            break;
        }
        callBuffer[count++] = o;
    }
    PyObject* args = PyTuple_New(count);
    for (int i = 0; i < count; i++) {
        Py_XINCREF(callBuffer[i]);
        PyTuple_SetItem(args, i, callBuffer[i]);
    }
    va_end(myargs);

    return PyObject_CallObject(callable, args);
}

#define IS_SINGLE_ARG(_fmt) ((_fmt[0]) != '\0' && (_fmt[1]) == '\0')

#undef PyObject_CallFunction
PyObject* PyObject_CallFunction(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
        return _PyObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _PyObject_Call1(callable, args, NULL, IS_SINGLE_ARG(fmt));
}

PyObject* _PyObject_CallFunction_SizeT(PyObject* callable, const char* fmt, ...) {
    if (fmt == NULL || fmt[0] == '\0') {
        return _PyObject_Call1(callable, NULL, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    PyObject* args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _PyObject_Call1(callable, args, NULL, IS_SINGLE_ARG(fmt));
}


PyObject* PyObject_CallMethod(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return _PyObject_CallMethod1(object, method, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _PyObject_CallMethod1(object, method, args, IS_SINGLE_ARG(fmt));
}

PyObject* _PyObject_CallMethod_SizeT(PyObject* object, const char* method, const char* fmt, ...) {
    PyObject* args;
    if (fmt == NULL || fmt[0] == '\0') {
        return _PyObject_CallMethod1(object, method, NULL, 0);
    }
    va_list va;
    va_start(va, fmt);
    args = Py_VaBuildValue(fmt, va);
    va_end(va);
    return _PyObject_CallMethod1(object, method, args, IS_SINGLE_ARG(fmt));
}

PyObject * PyObject_CallMethodObjArgs(PyObject *a, PyObject *b, ...)  {
    printf("PyObject_CallMethodObjArgs not implemented in capi_jni - exiting\n");
    exit(-1);
}

PyAPI_FUNC(int) PyArg_Parse(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(PyTuple_Pack(1, a), b, args);
    va_end(args);
    return result;
}

PyAPI_FUNC(int) _PyArg_Parse_SizeT(PyObject* a, const char* b, ...) {
    va_list args;
    va_start(args, b);
    int result = (int) PyArg_VaParse(PyTuple_Pack(1, a), b, args);
    va_end(args);
    return result;
}


/*
 * This dummy implementation is needed until we can properly transition the PyThreadState data structure to native.
 */

PyThreadState mainThreadState; // dummy
PyThreadState * PyThreadState_Get() {
    mainThreadState.interp = (PyInterpreterState*) 0;
    return &mainThreadState;
}

char _PyByteArray_empty_string[] = "";

/*
 * The following source files contain code that can be compiled directly and does not need to be called via stubs in Sulong:
 */

#include "_warnings.c"
#include "boolobject.c"
#include "complexobject.c"
#include "dictobject.c"
#include "modsupport_shared.c"
