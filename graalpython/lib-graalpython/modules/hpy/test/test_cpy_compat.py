# MIT License
# 
# Copyright (c) 2020, 2023, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from .support import HPyTest
import pytest


@pytest.mark.usefixtures('skip_nfi')
class TestCPythonCompatibility(HPyTest):

    # One note about the supports_refcounts() in the tests below: on
    # CPython, handles are actually implemented as INCREF/DECREF, so we can
    # check e.g. after an HPy_Dup the refcnt is += 1. However, on PyPy they
    # are implemented in a completely different way which is unrelated to the
    # refcnt (this is the whole point of HPy, after all :)). So in many of the
    # following ttests, checking the actual result of the function doesn't
    # really make sens on PyPy. We still run the functions to ensure they do
    # not crash, though.

    def test_frompyobject(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                PyObject *o = PyList_New(0);
                Py_ssize_t initial_refcount = Py_REFCNT(o);
                HPy h = HPy_FromPyObject(ctx, o);
                Py_ssize_t final_refcount = Py_REFCNT(o);

                PyList_Append(o, PyLong_FromLong(1234));
                PyList_Append(o, PyLong_FromSsize_t(final_refcount -
                                                    initial_refcount));
                Py_DECREF(o);
                return h;
            }
            @EXPORT(f)
            @INIT
        """)
        x = mod.f()
        assert x[0] == 1234
        assert len(x) == 2
        if self.supports_refcounts():
            assert x == [1234, +1]

    def test_frompyobject_null(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                HPy h = HPy_FromPyObject(ctx, NULL);
                if (HPy_IsNull(h)) {
                    return HPy_Dup(ctx, ctx->h_True);
                }
                else {
                    return HPy_Dup(ctx, ctx->h_False);
                }
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f()

    def test_aspyobject(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                PyObject *o = HPy_AsPyObject(ctx, arg);
                long val = PyLong_AsLong(o);
                Py_DecRef(o);
                return HPyLong_FromLong(ctx, val*2);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f(21) == 42

    def test_aspyobject_null(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                PyObject *o = HPy_AsPyObject(ctx, HPy_NULL);
                if (o == NULL) {
                    return HPy_Dup(ctx, ctx->h_True);
                }
                else {
                    return HPy_Dup(ctx, ctx->h_False);
                }
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f()

    def test_aspyobject_custom_class(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_O)
            static HPy f_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                PyObject *o = HPy_AsPyObject(ctx, arg);
                PyObject *o_res = PyObject_CallMethod(o, "foo", "");
                HPy h_res = HPy_FromPyObject(ctx, o_res);
                Py_DecRef(o);
                Py_DecRef(o_res);
                return h_res;
            }
            @EXPORT(f)
            @INIT
        """)
        class MyClass:
            def foo(self):
                return 1234
        obj = MyClass()
        assert mod.f(obj) == 1234

    def test_hpy_close(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                PyObject *o = PyList_New(0);

                HPy h = HPy_FromPyObject(ctx, o);
                Py_ssize_t initial_refcount = Py_REFCNT(o);
                HPy_Close(ctx, h);
                Py_ssize_t final_refcount = Py_REFCNT(o);

                Py_DECREF(o);
                return HPyLong_FromLong(ctx, (long)(final_refcount -
                                                    initial_refcount));
            }
            @EXPORT(f)
            @INIT
        """)
        x = mod.f()
        if self.supports_refcounts():
            assert x == -1

    def test_hpy_dup(self):
        mod = self.make_module("""
            #include <Python.h>
            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                PyObject *o = PyList_New(0);

                HPy h = HPy_FromPyObject(ctx, o);
                Py_ssize_t initial_refcount = Py_REFCNT(o);
                HPy h2 = HPy_Dup(ctx, h);
                Py_ssize_t final_refcount = Py_REFCNT(o);

                HPy_Close(ctx, h);
                HPy_Close(ctx, h2);
                Py_DECREF(o);
                return HPyLong_FromLong(ctx, (long)(final_refcount -
                                                    initial_refcount));
            }
            @EXPORT(f)
            @INIT
        """)
        x = mod.f()
        if self.supports_refcounts():
            assert x == +1

    def test_many_handles(self):
        mod = self.make_module("""
            #include <Python.h>
            #define NUM_HANDLES  10000

            HPyDef_METH(f, "f", f_impl, HPyFunc_NOARGS)
            static HPy f_impl(HPyContext *ctx, HPy self)
            {
                PyObject *o = PyList_New(0);
                Py_ssize_t final_refcount;

                Py_ssize_t result = -42;
                HPy handles[NUM_HANDLES];
                int i;
                Py_ssize_t initial_refcount = Py_REFCNT(o);
                for (i = 0; i < NUM_HANDLES; i++)
                    handles[i] = HPy_FromPyObject(ctx, o);
                for (i = 0; i < NUM_HANDLES; i++)
                    if (HPy_IsNull(handles[i]))
                        goto error;
                for (i = 0; i < NUM_HANDLES; i++)
                    HPy_Close(ctx, handles[i]);
                final_refcount = Py_REFCNT(o);
                result = final_refcount - initial_refcount;

             error:
                return HPyLong_FromLong(ctx, (long)result);
            }
            @EXPORT(f)
            @INIT
        """)
        assert mod.f() == 0
