package by.list.reader;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.util.*;

/** Isolated login surface. Never has the reader's JavaScript bridge. */
public final class YandexLoginActivity extends Activity {
    private WebView web;private String state;private long started;private TextView status;private LinearLayout root;private boolean finishing;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);state=getIntent().getStringExtra("state");started=getIntent().getLongExtra("started",0);if(state==null||System.currentTimeMillis()-started>600000){finish();return;}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,8);root.setBackgroundColor(Color.WHITE);setContentView(root);
        final int padding=(int)(16*getResources().getDisplayMetrics().density);root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(padding+insets.getSystemWindowInsetLeft(),padding+insets.getSystemWindowInsetTop(),padding+insets.getSystemWindowInsetRight(),padding/2+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});root.requestApplyInsets();
        Button back=new Button(this);back.setText("Отмена · вернуться в ilenbook");back.setOnClickListener(v->finish());root.addView(back);
        status=new TextView(this);status.setText("Экспериментальное подключение Яндекс Книг через Bookmate. Вход выполняется на сайте Яндекса. ilenbook получает доступ к данным аккаунта; здесь используются только запросы чтения.");status.setTextColor(Color.BLACK);status.setPadding(4,10,4,10);root.addView(status);
        LinearLayout actions=new LinearLayout(this);Button browser=new Button(this);browser.setText("Открыть в браузере");browser.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(authUrl())));}catch(Exception e){status.setText("Браузер недоступен");}});actions.addView(browser,new LinearLayout.LayoutParams(0,-2,1));Button paste=new Button(this);paste.setText("Ссылка возврата");paste.setOnClickListener(v->pasteCallback());actions.addView(paste,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSaveFormData(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return request.isForMainFrame()&&navigate(request.getUrl().toString());}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){if(navigate(url))return;try{status.setText("Вход на "+Uri.parse(url).getHost()+" · пароль вводится только на странице Яндекса");}catch(Exception ignored){}}
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();status.setText("Не удалось проверить защищённое соединение. Вход остановлен.");}
        });root.addView(web,new LinearLayout.LayoutParams(-1,0,1));web.loadUrl(authUrl());
    }
    private String authUrl(){return "https://oauth.yandex.ru/authorize?response_type=token&client_id="+YandexProtocol.CLIENT+"&lang=ru&state="+Uri.encode(state);}
    private boolean navigate(String value){Uri u=Uri.parse(value);String host=u.getHost();if(YandexProtocol.CALLBACK.equalsIgnoreCase(host)){accept(value);return true;}boolean trusted="https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&host!=null&&(host.equals("yandex.ru")||host.endsWith(".yandex.ru")||host.equals("yandex.com")||host.endsWith(".yandex.com")||host.equals("yandex.by")||host.endsWith(".yandex.by"));if(!trusted){if(web!=null)web.stopLoading();status.setText("Этот переход не относится к входу Яндекса. Используйте кнопку браузера при необходимости.");return true;}return false;}
    private void accept(String url){if(finishing)return;try{if(System.currentTimeMillis()-started>600000)throw new Exception("Время входа истекло. Начните заново.");String token=YandexProtocol.callbackToken(url,state);finishing=true;if(web!=null)web.stopLoading();setResult(RESULT_OK,new Intent().putExtra("token",token));finish();}catch(Exception e){status.setText(e.getMessage());if(web!=null)web.stopLoading();}}
    private void pasteCallback(){EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setHint("Полная ссылка возврата из адресной строки");input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);new AlertDialog.Builder(this).setTitle("Завершение входа из браузера").setMessage("После входа скопируйте полный адрес страницы возврата и вставьте сюда. Ссылка проверяется только на устройстве и не отправляется на сторонние сайты.").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Подключить",(d,w)->accept(input.getText().toString().trim())).show();}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
