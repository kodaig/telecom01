package com.example.rondo.telecom01

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toSend.setOnClickListener{
            val intent = Intent(this, SendActivity::class.java)
            startActivity(intent)
        }

        toReceive.setOnClickListener{
            val intent = Intent(this, ReceiveActivity::class.java)
            startActivity(intent)
        }
    }
}
