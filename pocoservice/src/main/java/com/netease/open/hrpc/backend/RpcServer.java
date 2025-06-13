package com.netease.open.hrpc.backend;

import static com.netease.open.hrpc.backend.MethodMatcher.findMatchingMethod;

import android.annotation.TargetApi;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang3.exception.ExceptionUtils;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by adolli on 2017/7/12.
 */

public class RpcServer extends NanoHTTPD {
    private static final String TAG = "RpcServer";

    private final Map<String, Object> objUriStore_ = new HashMap<>();

    public RpcServer(String hostname, int port) throws IOException {
        super(hostname, port);
        this.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> body = new HashMap<>();
        try {
            session.parseBody(body);
        } catch (IOException|ResponseException e) {
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, ExceptionUtils.getStackTrace(e));
        }
        JSONObject req = null;
        try {
            req = new JSONObject(body.get("postData"));
        } catch (JSONException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, ExceptionUtils.getStackTrace(e));
        }
        JSONObject resp = this.onRequest(req);
        if (resp != null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", resp.toString());
        } else {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "server internal error. response is null");
        }
    }

    public JSONObject onRequest(JSONObject req) {
        JSONObject resp = null;
        String sessionId = null;
        String reqId = null;
        String uri = null;
        JSONArray method = null;
        Log.d(TAG, "req = " + req);
        try {
            sessionId = req.getString("session_id");
            reqId = req.getString("id");
        } catch (JSONException e0) {
            e0.printStackTrace();
            return null;
        }
        try {
            uri = req.getString("uri");
            method = req.getJSONArray("method");
        } catch (JSONException e0) {
            e0.printStackTrace();
            return this.buildErrorResponse(reqId, sessionId, "RequestArgumentError", "Request argument error.", "", ExceptionUtils.getStackTrace(e0));
        }

        Object obj = this.get(uri);
        if (obj == null) {
            return this.buildErrorResponseObjectNotFound(reqId, sessionId, uri);
        }

        boolean shouldCacheIntermediateObj = method.length() != 0;
        Object _this = obj;
        String _overloadMethodName = "";
        List<java.lang.reflect.Method> _overloadMethods = new LinkedList<>();
        try {
            for (int i = 0; i < method.length(); i++) {
                JSONArray operation = method.getJSONArray(i);
                String operator = operation.getString(0);
                if (operator.equals("getattr")) {
                    _this = obj;
                    String params = operation.getString(1);
                    Class<?> objCls = obj.getClass();

                    for (java.lang.reflect.Method m : objCls.getMethods()) {
                        // 先把所有名字相同的方法都存起来，在调用的时候根据参数类型去选出重载的方法
                        if (m.getName().equals(params)) {
                            _overloadMethods.add(m);
                        }
                        _overloadMethodName = params;
                    }
                    if (_overloadMethods.size() == 0) {
                        Field field = objCls.getDeclaredField(params);
                        obj = field.get(obj);
                    }

                } else if (operator.equals("call")) {
                    // 构造要调用的参数
                    ArrayList<Object> calcArguments = new ArrayList<>();
                    JSONArray params = operation.getJSONArray(1);
                    for (int j = 0; j < params.length(); j++) {
                        String paramType = params.getJSONArray(j).getString(0);
                        Object param = params.getJSONArray(j).get(1);
                        if (paramType.equals("uri")) {
                            Object value = this.get(param.toString());
                            if (value == null) {
                                return this.buildErrorResponseObjectNotFound(reqId, sessionId, uri);
                            }
                            calcArguments.add(value);
                        } else {
                            if (param.equals(JSONObject.NULL)) {
                                param = null;
                            }
                            calcArguments.add(param);
                        }
                    }

                    java.lang.reflect.Method func = findMatchingMethod(_overloadMethods, _overloadMethodName, calcArguments);
                    if (func != null) {
                        obj = func.invoke(_this, calcArguments.toArray());
                    } else {
                        throw new NoSuchMethodException(String.format(
                                "\"%s\" does not have (overload)method name \"%s\", arguments are %s, arguments.size() is %s",
                                obj, _overloadMethodName, calcArguments, calcArguments.size()));
                    }
                    _overloadMethodName = "";

                } else if (operator.equals("getitem")) {
                    try {
                        int params = operation.getInt(1);
                        try {
                            obj = ((Object[]) obj)[params];
                        } catch (ClassCastException e) {
                            obj = ((List) obj).get(params);
                        }
                    } catch (JSONException e) {
                        String params = operation.getString(1);
                        obj = ((Map<String, Object>) obj).get(params);
                    }
                } else if (operator.equals("len")) {
                    try {
                        obj = ((Object[]) obj).length;
                    } catch (ClassCastException __e) {
                        try {
                            obj = ((List) obj).size();
                        } catch (ClassCastException __e2) {
                            obj = ((Map<Object, Object>) obj).size();
                        }
                    }

                } else if (operator.equals("=")) {
                    Class<?> objCls = obj.getClass();

                    JSONArray params = operation.getJSONArray(1);
                    String fieldName = params.getString(0);
                    Object value = params.get(1);
                    Field field = objCls.getDeclaredField(fieldName);
                    field.set(obj, value);
                    shouldCacheIntermediateObj = false;

                } else if (operator.equals("=uri")) {
                    Class<?> objCls = obj.getClass();

                    JSONArray params = operation.getJSONArray(1);
                    String fieldName = params.getString(0);
                    String argUri = params.getString(1);
                    Object value = this.get(argUri);
                    if (value == null) {
                        return this.buildErrorResponseObjectNotFound(reqId, sessionId, uri);
                    }
                    Field field = objCls.getDeclaredField(fieldName);
                    field.set(obj, value);

                    obj = value;
                    shouldCacheIntermediateObj = false;

                } else if (operator.equals("del")) {
                    this.remove(uri);
                    shouldCacheIntermediateObj = false;
                }
            }


            boolean serializable = jsonSerializable(obj);
            if (!serializable) {
                Object intermediaObj = null;
                String intermediaUri = null;
                if (shouldCacheIntermediateObj) {
                    intermediaObj = obj;
                    intermediaUri = String.format("%s(%s)", intermediaObj.toString(), UUID.randomUUID());
                    this.store(intermediaUri, intermediaObj);
                }
                resp = this.buildResponse(reqId, sessionId, String.format("<Rpc remote object proxy of %s>", obj.toString()), intermediaUri);
            } else {
                resp = this.buildResponse(reqId, sessionId, JSONObject.wrap(obj), null);
            }

        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                cause = e;
            }
            resp = this.buildErrorResponse(reqId, sessionId, cause.getClass().getName(), cause.getMessage(), "", ExceptionUtils.getStackTrace(cause));
        }

        Log.d(TAG, "resp = " + resp);

        return resp;
    }

    private JSONObject buildResponse(String id, String sessionId, Object result, String uri) {
        JSONObject ret = new JSONObject();
        try {
            ret.put("id", id);
            ret.put("session_id", sessionId);
            ret.put("result", result);
            if (uri != null) {
                ret.put("uri", uri);
            }
        } catch (JSONException e) {
            // 不太可能会进这里
            e.printStackTrace();
        }
        return ret;
    }

    private JSONObject buildErrorResponse(String id, String sessionId, String errType, String errMsg, String errStack, String traceback) {
        JSONObject ret = new JSONObject();
        if (errMsg == null) {
            errMsg = "";
        }
        try {
            ret.put("id", id);
            ret.put("session_id", sessionId);

            JSONObject err = new JSONObject();
            err.put("type", errType);
            err.put("message", errMsg);
            err.put("stack", errStack);
            err.put("tb", traceback);
            ret.put("errors", err);
        } catch (JSONException e) {
            // 不太可能会进这里
            e.printStackTrace();
        }
        return ret;
    }

    private JSONObject buildErrorResponseObjectNotFound(String reqId, String sessionId, String uri) {
        return this.buildErrorResponse(reqId, sessionId, "RemoteObjectNotFoundException", String.format("RPC object not found. uri=\"%s\"", uri), "", "");
    }

    private String store(String uri, Object obj) {
        this.export(uri, obj);
        return uri;
    }

    private Object remove(String uri) {
        return this.objUriStore_.remove(uri);
    }

    private Object get(String uri) {
        Object obj = this.objUriStore_.get(uri);
        return obj;
    }

    public void export(String uri, Object obj) {
        this.objUriStore_.put(uri, obj);
    }

    public static boolean jsonSerializable(Object o) {
        if (o == null) {
            return true;
        }
        if (o instanceof JSONArray || o instanceof JSONObject) {
            return true;
        }
        if (o.equals(JSONObject.NULL)) {
            return true;
        }

        if (o instanceof List) {
            for (Object oo : (List) o) {
                boolean subElementSerializable = jsonSerializable(oo);
                if (!subElementSerializable) {
                    return false;
                }
            }
            return true;
        } else if (o.getClass().isArray()) {
            int length = Array.getLength(o);
            for (int i = 0; i < length; i ++) {
                Object arrayElement = Array.get(o, i);
                if (!jsonSerializable(arrayElement)) {
                    return false;
                }
            }
            return true;
        }
        if (o instanceof Map) {
            Map mapO = ((Map) o);
            for (Object k : mapO.entrySet()) {
                if (!jsonSerializable(k) || !jsonSerializable(mapO.get(k))) {
                    return false;
                }
            }
            return true;
        }
        if (o instanceof Boolean ||
                o instanceof Byte ||
                o instanceof Character ||
                o instanceof Double ||
                o instanceof Float ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Short ||
                o instanceof String) {
            return true;
        }
        return false;
    }
}
