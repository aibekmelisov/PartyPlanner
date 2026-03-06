package com.example.partyplanner.data.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static volatile ApiService API;

    public static ApiService api(String baseUrl) {
        if (API == null) {
            synchronized (RetrofitClient.class) {
                if (API == null) {
                    HttpLoggingInterceptor log = new HttpLoggingInterceptor();
                    log.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(log)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl) // ВАЖНО: с "/" в конце
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(client)
                            .build();

                    API = retrofit.create(ApiService.class);
                }
            }
        }
        return API;
    }
}
