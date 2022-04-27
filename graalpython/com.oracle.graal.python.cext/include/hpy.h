/* MIT License
 *  
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *  
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *  
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *  
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPy_H
#define HPy_H
#ifdef __cplusplus
extern "C" {
#endif

#ifndef HPY_UNIVERSAL_ABI
/*  It would be nice if we could include hpy.h WITHOUT bringing in all the
    stuff from Python.h, to make sure that people don't use the CPython API by
    mistake. How to achieve it, though? */
#   define PY_SSIZE_T_CLEAN
#   include <Python.h>
#endif

#include <stdlib.h>
#include <stdint.h>
#include <stdarg.h>

/* ~~~~~~~~~~~~~~~~ useful macros ~~~~~~~~~~~~~~~~ */

#ifdef __GNUC__
#   define _HPy_HIDDEN __attribute__((visibility("hidden")))
#   define _HPy_UNUSED __attribute__((unused))
#else
#   define _HPy_HIDDEN
#   define _HPy_UNUSED
#endif /* __GNUC__ */

#if defined(__clang__) || \
    (defined(__GNUC__) && \
     ((__GNUC__ >= 3) || \
      (__GNUC__ == 2) && (__GNUC_MINOR__ >= 5)))
#  define _HPy_NO_RETURN __attribute__((__noreturn__))
#elif defined(_MSC_VER)
#  define _HPy_NO_RETURN __declspec(noreturn)
#else
#  define _HPy_NO_RETURN
#endif

#if defined(_MSC_VER) && defined(__cplusplus) // MSVC C4576
#  define _hconv(h) {h}
#  define _hfconv(h) {h}
#  define _htsconv(h) {h}
#  define _hgconv(h) {h}
#else
#  define _hconv(h) ((HPy){h})
#  define _hfconv(h) ((HPyField){h})
#  define _htsconv(h) ((HPyThreadState){h})
#  define _hgconv(h) ((HPyGlobal){h})
#endif
/* ~~~~~~~~~~~~~~~~ HPyAPI declaration ~~~~~~~~~~~~~~~~ */

/* We have three different kind of API functions: */

/**
 * Public API functions which are exposed to the user, e.g.
 * ``HPy_Add`` or ``HPyType_FromSpec``. Generally speaking they are
 * thin shims dispatching to the actual implementation:
 *
 * * In CPython-ABI mode they directly call the corresponding Py* or
 *   ``HPyAPI_IMPL`` equivalent, e.g. ``PyObject_Add`` or
 *   ``ctx_Type_FromSpec``.
 *
 * * In Universal-ABI mode, they always resolve to an indirect call
 *   through ``HPyContext *``, i.e. ``ctx->ctx_Add(...)``, which on CPython
 *   dispaches to ``ctx_Add``.
 */
#define HPyAPI_FUNC   _HPy_UNUSED static inline

/**
 * CPython implementations for ``HPyAPI_FUNC``
 * functions. Generally speaking, they are put in ctx_*.c files and they are
 * prefixed by ctx\_.
 *
 * Some of these functions are needed by the CPython ABI mode, and by
 * CPython's implementation of hpy.universal: these can be found in
 * hpy/devel/src/runtime/ctx_*.c, e.g. ``ctx_Type_FromSpec`` and
 * ``ctx_Tuple_FromArray``.
 *
 * Some other are used ONLY by ``hpy.universal`` and can be found in
 * hpy/universal/src/ctx_*.c.
 */
#define HPyAPI_IMPL   _HPy_HIDDEN

/**
 * These functions are part of the public API but **not** of
 * the ABI. They are helpers which are meant to be compiled togeher with every
 * extension. E.g. ``HPyArg_Parse`` and ``HPyHelpers_AddType``.
 */
#define HPyAPI_HELPER _HPy_HIDDEN


/* ~~~~~~~~~~~~~~~~ Definition of the type HPy ~~~~~~~~~~~~~~~~ */

/* HPy handles are fully opaque: depending on the implementation, the _i can
   be either an integer or a pointer. A few examples:

   * in CPython ABI mode, ._i is a PyObject*

   * in Universal ABI mode, the meaning of ._i depends on the implementation:

       - CPython (i.e., the code in hpy/universal/src/): ._i is the bitwise
         invert of a PyObject*

       - PyPy: ._i is an index into a list

       - GraalPython: ???

       - Debug mode: _i is a pointer to a DebugHandle, which contains a
         another HPy among other stuff
 */
#ifndef GRAALVM_PYTHON_LLVM
typedef struct _HPy_s { intptr_t _i; } HPy;
typedef struct { intptr_t _i; } HPyField;
typedef struct { intptr_t _i; } HPyGlobal;
typedef struct { intptr_t _lst; } HPyListBuilder;
typedef struct { intptr_t _tup; } HPyTupleBuilder;
typedef struct { intptr_t _i; } HPyTracker;
typedef struct { intptr_t _i; } HPyThreadState;
#else
typedef struct _HPy_s { void* _i; } HPy;
typedef struct { void* _i; } HPyField;
typedef struct { void* _i; } HPyGlobal;
typedef struct { void* _lst; } HPyListBuilder;
typedef struct { void* _tup; } HPyTupleBuilder;
typedef struct { void* _i; } HPyTracker;
typedef struct { void* _i; } HPyThreadState;
#endif


/* A null handle is officially defined as a handle whose _i is 0. This is true
   in all ABI modes. */
#ifndef GRAALVM_PYTHON_LLVM
#define HPy_NULL _hconv(0)
#define HPy_IsNull(h) ((h)._i == 0)
#else
#define HPy_NULL _hconv(NULL)
#define HPy_IsNull(h) ((h)._i == NULL)
#endif

#define HPyField_NULL _hfconv(0)
#define HPyField_IsNull(f) ((f)._i == 0)

/* Convenience functions to cast between HPy and void*.  We need to decide
   whether these are part of the official API or not, and maybe introduce a
   better naming convetion. For now, they are needed for ujson. */
#ifndef GRAALVM_PYTHON_LLVM
static inline HPy HPy_FromVoidP(void *p) { return _hconv((intptr_t)p); }
static inline void* HPy_AsVoidP(HPy h) { return (void*)h._i; }
#else
static inline HPy HPy_FromVoidP(void *p) { return _hconv(p); }
static inline void* HPy_AsVoidP(HPy h) { return h._i; }
#endif


/* ~~~~~~~~~~~~~~~~ Definition of other types ~~~~~~~~~~~~~~~~ */

typedef struct _HPyContext_s HPyContext;

#ifdef HPY_UNIVERSAL_ABI
    typedef intptr_t HPy_ssize_t;
    typedef intptr_t HPy_hash_t;
    typedef uint32_t HPy_UCS4;
#else
    typedef Py_ssize_t HPy_ssize_t;
    typedef Py_hash_t HPy_hash_t;
    typedef Py_UCS4 HPy_UCS4;
#endif


/* ~~~~~~~~~~~~~~~~ Additional #includes ~~~~~~~~~~~~~~~~ */

#include "hpy/cpy_types.h"
#include "hpy/macros.h"
#include "hpy/hpyfunc.h"
#include "hpy/hpydef.h"
#include "hpy/hpytype.h"
#include "hpy/hpymodule.h"
#include "hpy/runtime/argparse.h"
#include "hpy/runtime/buildvalue.h"
#include "hpy/runtime/helpers.h"

#ifdef HPY_UNIVERSAL_ABI
#   include "hpy/universal/autogen_ctx.h"
#   include "hpy/universal/autogen_trampolines.h"
#   include "hpy/universal/misc_trampolines.h"
#else
//  CPython-ABI
#   include "hpy/runtime/ctx_funcs.h"
#   include "hpy/runtime/ctx_type.h"
#   include "hpy/cpython/misc.h"
#   include "hpy/cpython/autogen_api_impl.h"
#endif

#include "hpy/inline_helpers.h"

#ifdef __cplusplus
}
#endif
#endif /* HPy_H */
