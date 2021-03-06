import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator
import io.opentracing.util.GlobalTracer
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CompletableFuture

class ApacheHttpAsyncClientCallbackTest extends HttpClientTest<ApacheHttpAsyncClientDecorator> {

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.createDefault()

  def setupSpec() {
    client.start()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    assert method == "GET"

    def hasParent = GlobalTracer.get().activeSpan() != null

    HttpGet request = new HttpGet(uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def responseFuture = new CompletableFuture<>()

    client.execute(request, new FutureCallback<HttpResponse>() {

      @Override
      void completed(HttpResponse result) {
        if (hasParent && GlobalTracer.get().activeSpan() == null) {
          responseFuture.completeExceptionally(new Exception("Missing span in scope"))
        } else {
          responseFuture.complete(result.statusLine.statusCode)
        }
        callback?.call()
      }

      @Override
      void failed(Exception ex) {
        if (hasParent && GlobalTracer.get().activeSpan() == null) {
          responseFuture.completeExceptionally(new Exception("Missing span in scope"))
        } else {
          responseFuture.completeExceptionally(ex)
        }
      }

      @Override
      void cancelled() {
        responseFuture.cancel(true)
      }
    })

    return responseFuture.get()
  }

  @Override
  ApacheHttpAsyncClientDecorator decorator() {
    return ApacheHttpAsyncClientDecorator.DECORATE
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }
}
