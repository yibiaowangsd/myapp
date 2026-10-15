#!/usr/bin/env python3
"""Verify busy_timeout's result-set contract using SQLite, without Android mocks."""
import ctypes
import ctypes.util
import json
import sqlite3

def verify():
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE preserved_record (id TEXT PRIMARY KEY)")
    db.execute("INSERT INTO preserved_record VALUES ('preserved')")
    cursor = db.execute("PRAGMA busy_timeout=5000")
    assert cursor.description and cursor.description[0][0] == "timeout"
    assert cursor.fetchone() == (5000,)
    cursor.close()
    assert db.execute("PRAGMA busy_timeout").fetchone() == (5000,)
    assert db.execute("SELECT id FROM preserved_record").fetchone() == ("preserved",)
    assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
    db.close()
    result = {"sqliteVersion": sqlite3.sqlite_version, "pragmaReturnsRows": True,
              "timeoutMs": 5000, "recordPreserved": True, "integrity": "ok"}
    library = ctypes.util.find_library("sqlite3")
    if library:
        api = ctypes.CDLL(library)
        pointer = ctypes.c_void_p
        api.sqlite3_open.argtypes = [ctypes.c_char_p, ctypes.POINTER(pointer)]
        api.sqlite3_open.restype = ctypes.c_int
        api.sqlite3_prepare_v2.argtypes = [pointer, ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(pointer), pointer]
        api.sqlite3_prepare_v2.restype = ctypes.c_int
        api.sqlite3_step.argtypes = [pointer]
        api.sqlite3_step.restype = ctypes.c_int
        api.sqlite3_column_int.argtypes = [pointer, ctypes.c_int]
        api.sqlite3_column_int.restype = ctypes.c_int
        api.sqlite3_finalize.argtypes = [pointer]
        api.sqlite3_close.argtypes = [pointer]
        handle, statement = pointer(), pointer()
        assert api.sqlite3_open(b":memory:", ctypes.byref(handle)) == 0
        try:
            assert api.sqlite3_prepare_v2(handle, b"PRAGMA busy_timeout=5000", -1, ctypes.byref(statement), None) == 0
            status = api.sqlite3_step(statement)
            assert status == 100  # SQLITE_ROW, not SQLITE_DONE (101)
            assert api.sqlite3_column_int(statement, 0) == 5000
            result["nativeStepResult"] = status
            result["nativeStepResultName"] = "SQLITE_ROW"
        finally:
            if statement:
                api.sqlite3_finalize(statement)
            api.sqlite3_close(handle)
    print(json.dumps(result, indent=2))
    return result

if __name__ == "__main__":
    verify()
