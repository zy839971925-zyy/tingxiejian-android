package com.example.tingxiejian;
import android.content.Context;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Actual shipped HTTP client with a local fixture; only Android storage APIs are stand-ins. */
public final class CloudSnapshotCheck {
    public static void main(String[] args) throws Exception {
        AtomicReference<String> bearer = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean echoKey = new AtomicBoolean();
        CountDownLatch blocked=new CountDownLatch(1),unblock=new CountDownLatch(1);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService handlers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"http-fixture");t.setDaemon(true);return t;});
        server.setExecutor(handlers);
        server.createContext("/blocked",exchange->{
            blocked.countDown();try{unblock.await(4,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
            try{exchange.sendResponseHeaders(200,0);}catch(java.io.IOException ignored){}finally{exchange.close();}
        });
        server.createContext("/trickle",exchange->{
            exchange.sendResponseHeaders(200,0);
            try{for(int i=0;i<80;i++){exchange.getResponseBody().write(' ');exchange.getResponseBody().flush();Thread.sleep(10);}}
            catch(java.io.IOException|InterruptedException ignored){}finally{exchange.close();}
        });
        server.createContext("/", exchange -> {
            requests.incrementAndGet(); bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] reply=(echoKey.get() ? "{\"error\":{\"message\":\"old-provider-key\"}}" : "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(echoKey.get() ? 401 : 200,reply.length); exchange.getResponseBody().write(reply); exchange.close();
        });
        server.start();
        try {
            Context context=new Context();
            context.preferences.edit().putString("apiKey","old-provider-key")
                .putString("baseUrl","http://127.0.0.1:"+server.getAddress().getPort()).apply();
            Method require=Cloud.class.getDeclaredMethod("requireBaseUrl",Context.class); require.setAccessible(true);
            Object snapshot=require.invoke(null,context);
            // Settings switches provider/key between validation and request construction.
            context.preferences.edit().putString("apiKey","new-provider-key").putString("baseUrl","https://new.example/v1").apply();
            Method post=Cloud.class.getDeclaredMethod("post",Context.class,snapshot.getClass(),String.class,JSONObject.class,int.class);
            post.setAccessible(true); post.invoke(null,context,snapshot,"",new JSONObject().put("fixture","fixed"),1000);
            if (!"Bearer old-provider-key".equals(bearer.get())) throw new AssertionError("old endpoint received a different provider's current key");
            echoKey.set(true);
            try {
                post.invoke(null,context,snapshot,"",new JSONObject().put("fixture","fixed"),1000);
                throw new AssertionError("server error was accepted");
            } catch (InvocationTargetException failure) {
                Cloud.ApiException api = (Cloud.ApiException) failure.getCause();
                if (api.hint.contains("old-provider-key")) throw new AssertionError("server error exposed bearer token");
            }
            context.preferences.edit().putString("apiKey","").apply();
            try { Cloud.chat(context,"fixture","fixed"); throw new AssertionError("unconfigured client sent content"); }
            catch (Cloud.ApiException expected) { }
            if (requests.get()!=2) throw new AssertionError("empty key must perform zero requests");
            String invalidKey="invalid\nsecret-value";
            context.preferences.edit().putString("apiKey",invalidKey)
                    .putString("baseUrl","http://127.0.0.1:"+server.getAddress().getPort()).apply();
            try{Cloud.chat(context,"fixture","fixed");throw new AssertionError("control-character key accepted");}
            catch(Cloud.ApiException error){if(error.hint.contains(invalidKey))throw new AssertionError("invalid header error exposed key");}
            AtomicReference<Throwable> cancelled=new AtomicReference<>();
            Thread request=new Thread(()->{try{post.invoke(null,context,snapshot,"blocked",new JSONObject(),20_000);}
                catch(Throwable failure){cancelled.set(failure);}},"blocked-request-fixture");
            request.start();if(!blocked.await(2,TimeUnit.SECONDS))throw new AssertionError("blocked fixture not reached");
            request.interrupt();request.join(2000);
            if(request.isAlive()||cancelled.get()==null)throw new AssertionError("interrupted HTTP request must release its worker promptly");
            unblock.countDown();long start=System.nanoTime();
            try{post.invoke(null,context,snapshot,"trickle",new JSONObject(),150);throw new AssertionError("trickle bypassed total deadline");}
            catch(InvocationTargetException expected){}
            if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>2000)throw new AssertionError("trickle request exceeded bounded deadline");
            System.out.println("cloud request snapshots/cancellation: 6 checks passed (real loopback HTTP)");
        } finally { unblock.countDown();server.stop(0);handlers.shutdownNow(); }
    }
}
