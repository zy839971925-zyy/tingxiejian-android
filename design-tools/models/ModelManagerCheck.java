package com.example.tingxiejian;
import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.json.*;
public final class ModelManagerCheck {
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("model-manager-check").toFile();
  File assets=new File(root,"assets"),models=new File(assets,"model");models.mkdirs();
  JSONArray rows=new JSONArray();byte[] bytes={1,2,3};
  for(String name:new String[]{"stream-encoder.onnx","stream-decoder.onnx","stream-joiner.onnx","stream-tokens.txt"}) {
   File f=new File(models,name);Files.write(f.toPath(),bytes);
   rows.put(new JSONObject().put("filename",name).put("pack","core-streaming").put("expected_bytes",3).put("sha256",ModelInstaller.sha256(f)));
  }
  rows.put(new JSONObject().put("filename","offline-paraformer.onnx").put("pack","accurate-finalizer").put("expected_bytes",3).put("sha256",JSONObject.NULL));
  String hash=ModelInstaller.sha256(new File(models,"stream-encoder.onnx"));
  for(String name:new String[]{"encoder.int8.onnx","tokenizer/vocab.json"})rows.put(new JSONObject()
    .put("filename","qwen3-asr-0.6b-int8/"+name).put("pack","qwen3-asr-0.6b-int8")
    .put("expected_bytes",3).put("sha256",hash).put("redistribution","verified"));
  Files.write(new File(assets,"model-manifest.json").toPath(),new JSONObject().put("schema_version",1).put("models",rows).toString().getBytes("UTF-8"));
  Context context=new Context(new File(root,"private"),assets);
  try{ModelPrep.prepare(context,null);}catch(IOException e){throw new AssertionError("core setup must not require optional model assets",e);}
  ModelInstallerCheck.check(ModelPrep.ready(context),"core setup ready without optional assets");
  ModelInstallerCheck.check(ModelPrep.totalBytes(context)==12 && ModelPrep.doneBytes(context)==12,"core byte progress excludes optional packs");
  ModelInstallerCheck.check(!new File(ModelPrep.dir(context),"offline-paraformer.onnx").exists(),"optional finalizer never unpacked by core setup");
  ModelInstallerCheck.rejected(()->ModelManager.ensure(context,"offline-paraformer.onnx"));
  ModelInstallerCheck.check(ModelPrep.ready(context),"missing finalizer leaves core usable");
  ModelInstallerCheck.rejected(()->ModelManager.ensure(context,"../escape"));
  ModelInstallerCheck.check(!ModelManager.readyQwen(context) && !ModelManager.availableQwen(context),"missing optional Qwen is honest");
  // A bundled pack must be selectable before extraction, with observable preparation progress.
  File bundledRoot=new File(root,"bundled-assets"),bundledModel=new File(bundledRoot,"model/qwen3-asr-0.6b-int8");
  new File(bundledModel,"tokenizer").mkdirs();
  Files.copy(new File(assets,"model-manifest.json").toPath(),new File(bundledRoot,"model-manifest.json").toPath());
  Files.write(new File(bundledModel,"encoder.int8.onnx").toPath(),bytes);
  Files.write(new File(bundledModel,"tokenizer/vocab.json").toPath(),bytes);
  Context bundled=new Context(new File(root,"bundled-private"),bundledRoot);
  ModelInstallerCheck.check(ModelManager.bundledQwen(bundled) && ModelManager.availableQwen(bundled)
    && !ModelManager.readyQwen(bundled),"bundled, available and prepared are distinct states");
  final long[] progressed={0};
  ModelManager.prepare(bundled,ModelManager.Pack.QWEN3,(done,total,file)->{
    progressed[0]=done;ModelInstallerCheck.check(total==6,"Qwen progress total");
  });
  ModelInstallerCheck.check(progressed[0]==6 && ModelManager.readyQwen(bundled),"bundled Qwen setup reports real progress and activates pack");
  File first=ModelManager.importQwen(context,name->new ByteArrayInputStream(bytes),null);
  ModelInstallerCheck.check(ModelManager.readyQwen(context) && ModelManager.availableQwen(context),"verified import reports available");
  ModelInstallerCheck.check(ModelManager.ensureQwen(context).equals(first),"ensure loads imported pack without APK assets");
  ModelInstallerCheck.check(ModelManager.ensure(context,"qwen3-asr-0.6b-int8/encoder.int8.onnx").isFile(),"filename ensure resolves active generation");
  ModelInstallerCheck.rejected(()->ModelManager.importQwen(context,name->new ByteArrayInputStream(new byte[]{9,9,9}),null));
  ModelInstallerCheck.check(ModelManager.qwenDir(context).equals(first) && ModelManager.readyQwen(context),"failed imported pack leaves active verified pack usable");
  ExecutorService executor=Executors.newFixedThreadPool(2);
  CountDownLatch sourceOpened=new CountDownLatch(1),continueImport=new CountDownLatch(1);
  try {
   Future<File> importer=executor.submit(()->ModelManager.importQwen(context,name->{
    sourceOpened.countDown();
    try{continueImport.await();}catch(InterruptedException e){throw new IOException(e);}
    return new ByteArrayInputStream(bytes);
   },null));
   ModelInstallerCheck.check(sourceOpened.await(2,TimeUnit.SECONDS),"blocking import source reached");
   Future<Boolean> readiness=executor.submit(()->ModelManager.readyQwen(context));
   try{
    ModelInstallerCheck.check(readiness.get(1,TimeUnit.SECONDS),"UI Qwen readiness remains usable during pack import");
    Future<Boolean> coreReadiness=executor.submit(()->ModelPrep.ready(context));
    ModelInstallerCheck.check(coreReadiness.get(1,TimeUnit.SECONDS),"UI core readiness remains usable during optional pack import");
   }
   catch(TimeoutException e){throw new AssertionError("UI readiness blocked by long model import",e);}
   finally{continueImport.countDown();}
   importer.get(2,TimeUnit.SECONDS);
  }finally{continueImport.countDown();executor.shutdownNow();executor.awaitTermination(2,TimeUnit.SECONDS);}
  System.out.println("model manager: capability setup, optional failure, verified import checks passed");
 }
}
