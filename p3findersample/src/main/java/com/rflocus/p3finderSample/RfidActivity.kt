package com.rflocus.p3finderSample

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.rflocus.rfid.*
import com.rflocus.rfid.Enum
import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * RFIDリーダーの操作を管理するためのAndroid Activityクラスです。
 * GenericReaderListenerおよびGenericReaderHelper.GenericReaderHelperListenerインターフェイスを実装しています。
 */
open class RfidActivity : AppCompatActivity(), GenericReaderListener, GenericReaderHelper.GenericReaderHelperListener {

    companion object {
        // GenericReaderのインスタンスを保持する静的変数です。
        var mReader: GenericReader? = null
    }

    // Activityが生成された時に呼ばれるメソッドです。
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // GenericReaderHelperを利用してリーダータイプをロードします。
        val readerHelper = GenericReaderHelper(this, this)
        val readerType = readerHelper.loadReaderType()
        // リーダーを取得し、接続を試みます。
        mReader = GenericReader.getReader(readerType, applicationContext, this)
        mReader?.connect()
    }

    // リーダータイプが変更されたときに呼ばれるメソッドです。
    override fun onReaderTypeChanged(readerType: Enum.ReaderType) {
        // 新しいリーダータイプに基づいてリーダーを取得し、接続します。
        mReader = GenericReader.getReader(readerType, applicationContext, this)
        mReader?.connect()
    }

    // Activityが破棄される際に呼ばれるメソッドです。
    override fun onDestroy() {
        // リーダーの接続を切断します。
        mReader?.disconnect()
        super.onDestroy()
    }

    // タグデータが報告された際に呼ばれるメソッドです。
    override fun reportTag(tagData: TagData) {
        // タグデータの処理を行います。
    }

    // 単一のタグデータが報告された際に呼ばれるメソッドです。
    override fun reportOneTag(tagData: TagData) {
        // 単一のタグデータの処理を行います。
    }

    // リーダーステータスが報告された際に呼ばれるメソッドです。
    override fun reportReaderStatus(status: Enum.ReaderStatus) {
        if (status == Enum.ReaderStatus.connected) {
            // リーダーが接続された際の処理を行います。
        }
    }

    // コマンド実行結果が報告された際に呼ばれるメソッドです。
    override fun reportCommand(cmd: Enum.ReaderCommand, result: Boolean, detail: String?) {
        if (cmd == Enum.ReaderCommand.startInventory && result) {
            // 読取開始コマンドが成功した場合の処理を行います。
        } else if (cmd == Enum.ReaderCommand.stopInventory && result) {
            // 読取停止コマンドが成功した場合の処理を行います。
        }
    }

    // トリガーが押された際に報告されるメソッドです。
    override fun reportTrigger(pressed: Boolean) {
        if(pressed){//トリガーが押された場合
            //読取開始します。
            mReader?.startInventory()
        }else{//トリガーが離された場合
            //読取を終了します。
            mReader?.stopInventory()
        }
    }

    // バーコードが報告された際に呼ばれるメソッドです。
    override fun reportBarcode(barcode: String) {
        // バーコードデータの処理を行います。
    }

    // バッテリー状態が変更された際に呼ばれるメソッドです。
    override fun reportBatteryStateChanged(status: Int) {
        // バッテリー状態の変更に応じた処理を行います。
    }


    //for keyence trigger
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isKeyenceTrigger(keyCode)) {
            reportTrigger(true)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isKeyenceTrigger(keyCode)) {
            reportTrigger(false)
        }
        return super.onKeyUp(keyCode, event)
    }

    fun isKeyenceTrigger(keyCode: Int): Boolean {
        if(mReader?.readerType == Enum.ReaderType.DX_RH1){
            if((keyCode == 507 || keyCode == 0)){
                return true
            }
        }
        return false
    }
}