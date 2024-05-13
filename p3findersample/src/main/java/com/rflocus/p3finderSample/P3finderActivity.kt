package com.rflocus.p3finderSample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import com.rflocus.p3finder.P3FinderSdk
import com.rflocus.p3finder.P3FinderView
import com.rflocus.p3finder.RSSIRangeParameter
import com.rflocus.p3finder.TagLocation
import com.rflocus.rfid.Enum
import com.rflocus.rfid.Enum.ReaderCommand
import com.rflocus.rfid.Enum.ReaderStatus
import com.rflocus.rfid.Filter
import com.rflocus.rfid.GenericReader
import com.rflocus.rfid.TagData
import java.util.*


class P3finderActivity : RfidActivity(){
    private val TAG = "P3FindSDK"
    private lateinit var mP3FinderView: P3FinderView
    private lateinit var mP3finderTask:P3finderTask
    private var mDoWorkItem:MenuItem? = null
    private var isInventorying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //add this before p3finder view layout
        P3FinderSdk.sdkInitialize(
            applicationContext
        ) { result, message ->
            Log.d(
                TAG,
                "onAuthenticated: $result,$message"
            )
        }
        setContentView(R.layout.activity_main)

        mP3FinderView = findViewById(R.id.p3finderView)
        mP3finderTask = P3finderTask(mReader)
        mP3finderTask.start()
    }
    
    override fun onDestroy() {
        mP3finderTask.stop()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_menu, menu)
        this.mDoWorkItem = menu?.getItem(0)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_doWork ->{
                val flag = item.title == getString(R.string.btn_start)
                doWork(flag)
            }
            R.id.action_reset ->{
                mP3FinderView.reset()
                mP3finderTask.reset()
            }

            R.id.action_license ->{
                val alert = AlertDialog.Builder(this)
                alert.setTitle(R.string.title_remove_license)
                alert.setMessage(R.string.text_remove_license)
                alert.setNegativeButton("Cancel", null)
                alert.setPositiveButton(
                    "Yes"
                ) { dialog, which -> P3FinderSdk.removeLicense() }
                alert.show()
            }

            R.id.action_rssiRange ->{
                //default value: closeRssi = -52,nearRssi = -65,farRssi = -75
                val rssiRangeList = P3FinderSdk.getRssiRangeList()
                for(rssiRange in rssiRangeList){
                    Log.d(TAG,rssiRange.toString())
                }

                val rssiRangeParameter = RSSIRangeParameter("")
                rssiRangeParameter.closeRssi = -52f
                rssiRangeParameter.nearRssi = -65f
                rssiRangeParameter.farRssi = -75f
                P3FinderSdk.setRssiRangeParamter(rssiRangeParameter)
            }

            R.id.action_settings ->{
                //set readertype
                mReader?.let {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        return super.onOptionsItemSelected(item)
    }


    override fun reportTag(tagData: TagData) {
//        Log.d(TAG,tagData.toString())
        mP3finderTask.addEpc(tagData.epc)
        mP3FinderView.addTagData(tagData.epc, tagData.rssi, tagData.phase, tagData.frequency, tagData.polarization)
    }

    override fun reportTrigger(pressed: Boolean) {
        doWork(pressed)
    }

    override fun reportCommand(cmd: Enum.ReaderCommand,result: Boolean, detail: String?) {
        Log.d(TAG,"reportCommand. cmd=$cmd,result=$result,detail=$detail")
        if (cmd == ReaderCommand.startInventory && result) {
            isInventorying = true
            mP3FinderView.start()
            mP3finderTask.reset()
            runOnUiThread{
                mDoWorkItem?.setTitle(R.string.btn_stop)
            }
        } else if (cmd == ReaderCommand.stopInventory && result) {
            isInventorying = false
            mP3FinderView.stop()
            runOnUiThread{
                mDoWorkItem?.setTitle(R.string.btn_start)
            }
        }
    }

    override fun reportReaderStatus(status: Enum.ReaderStatus) {
        Log.d(TAG,"reportReaderStatus. status=$status")
        if(status == ReaderStatus.connected){
           //do something
            mReader?.let {
                //Keyence RH1:
                // power: 5~30dbm
                //channel:0:916.8,1:918.0;2:919.2;3:920.4;4:920.6;5:920.8
                it.power = 30f
                it.channel = intArrayOf(3,4,5)
            }

        }
    }

    fun doWork(pressed: Boolean){
        if(pressed){
            //start reading tags and computing the positions of tags.
            mReader?.startInventory()
        }else{
            mReader?.stopInventory()
        }
    }

    //P3Finder Helper Class
    inner class P3finderTask(val reader: GenericReader?){
        private val TAG = "P3FindSDKTask"
        private var mInitEpcFilter:Array<Filter>? = null
        private var mSetEpcMaskFlag = false //電波が出している間、読取タグ枚数がmaxを超えたら、一回だけepcFilterを設定します。

        private val LONG_DISTANCE_MODE = 2
        private val SHORT_DISTANCE_MODE = 3
        private var SEARCH_RSSI_THRESHOLD = -66f
        private var TARGET_MAX_COUNT = 8

        private var tmpEpcSet = HashSet<String>()

        init {
            if(reader?.readerType == Enum.ReaderType.SP1){
                TARGET_MAX_COUNT = 3
            }
        }
        private var mSearchTimer: Timer? = null

        private var mSearchTask: TimerTask = object : TimerTask() {
            override fun run() {
                if(!isInventorying) return
                val reader = reader?:return
                val maxRssi = mP3FinderView.maxRssi
//                Log.d(TAG,"maxRssi=$maxRssi,mSetEpcMaskFlag=$mSetEpcMaskFlag")
                if(maxRssi == -999f){ //読取なし
                    var rfmode:Int? = null
                    if(reader.readerType == Enum.ReaderType.RFR900_901 && reader.rfMode != LONG_DISTANCE_MODE){
                        rfmode = LONG_DISTANCE_MODE
                    }
                    if(rfmode != null || mSetEpcMaskFlag ){
                        setSearchParameters(rfmode,true)
                    }
                }else{//読取あり
                    var rfmode:Int? = null
                    if (reader.readerType == Enum.ReaderType.RFR900_901 && reader.rfMode == LONG_DISTANCE_MODE && maxRssi >= SEARCH_RSSI_THRESHOLD){
                        rfmode = SHORT_DISTANCE_MODE
                    }
                    var setFilterFlag = !mSetEpcMaskFlag && tmpEpcSet.size > TARGET_MAX_COUNT
                    if(rfmode != null || setFilterFlag){
                        setSearchParameters(rfmode,false)
                    }
                }
            }
        }

        private fun setSearchParameters(rfmode:Int?,clearFilterFlag:Boolean){
            Log.d(TAG,"setSearchParameters: maxRssi=${mP3FinderView.maxRssi},rfmode=$rfmode,clearFilterFlag=$clearFilterFlag")
            reader?.let {
                //Stop inventory before setting rf parameters
                it.stopInventory()
                rfmode?.let {mode ->   //change rfmode for bluebird reader.
                    /**
                     * Bluebird
                     * mReader.RF_SetRFMode(mode)
                     */
                    it.rfMode = mode
                }
                if(clearFilterFlag){
                    if(mInitEpcFilter != null){ // restore initial filter
                        it.setFilters(Enum.MemoryBank.EPC,mInitEpcFilter)
                    }else{ // clear current temporary epc masks.
                        /**
                         * sp1
                         * rfidScanner.clearFilter();
                         */

                        /**
                         * bluebird
                         * mReader.RF_RemoveSelection()
                         */
                        it.clearFilter()
                    }
                    mSetEpcMaskFlag = false
                    tmpEpcSet.clear()
                }else{
                    if(!mSetEpcMaskFlag){
                        val epcMasks = getTargetLocationList(TARGET_MAX_COUNT) //get the specified number of nearest tags
                        /**
                         * sp1
                         *  RFIDScannerFilter[] filters = new RFIDScannerFilter[masks.length];
                         *  for(int i=0;i<filters.length;i++){
                         *  filters[i] = new RFIDScannerFilter();
                         *  filters[i].bank = RFIDScannerFilter.Bank.UII;
                         *  filters[i].bitOffset = start;
                         *  filters[i].bitLength = (short)length;
                         *  filters[i].filterData = Tool.hexStringToBytes(masks[i],length);
                         * }
                         * RFIDScannerFilter.RFIDLogicalOpe logicalOpe = RFIDScannerFilter.RFIDLogicalOpe.OR;
                         * rfidScanner.setFilter(filters, logicalOpe);
                         */

                        /**
                         * bluebird
                         * SelectionCriterias sc = new SelectionCriterias();
                         * sc.makeCriteria(MemoryBank.EPC.value(),masks[0],start,length, SelectionCriterias.SCActionType.ASLINVA_DSLINVB);
                         * for(int i= 1;i<masks.length;i++){
                         * sc.makeCriteria(MemoryBank.EPC.value(),masks[i],start,length, SelectionCriterias.SCActionType.ASLINVA_NOTHING);
                         * }
                         *  int ret =  mReader.RF_SetSelection(sc);
                         */
                        it.setEpcFilter(epcMasks,32,96)
                        Log.d(TAG,"setEpcMasks:${epcMasks.toList()}")
                        mSetEpcMaskFlag = true
                    }
                }
                //restart inventory
                it.startInventory()
            }
        }

        private fun getTargetLocationList(count: Int): Array<String> {
            var targetList: List<TagLocation> = mP3FinderView.locationList
            if(targetList.size > count){
                Collections.sort(targetList) { o1, o2 -> (o1.distance - o2.distance).toInt() }
                targetList  =  targetList.subList(0, count)
            }
            val epcMasks = arrayListOf<String>()
            targetList.forEach {
                epcMasks.add(it.id)
            }
            return epcMasks.toTypedArray()
        }

        fun addEpc(epc:String){
            tmpEpcSet.add(epc)
        }

        fun start(){
            if (mSearchTimer == null) {
                mSearchTimer = Timer()
                mSearchTimer?.schedule(mSearchTask, 1000, 1000)
            }
        }

        fun stop(){
            if(mSearchTimer != null){
                mSearchTimer?.cancel()
                mSearchTimer = null
            }
        }

        fun reset(){
            tmpEpcSet.clear()
            if(mSetEpcMaskFlag) {
                if (mInitEpcFilter != null) { // restore initial filter
                    mReader?.setFilters(Enum.MemoryBank.EPC, mInitEpcFilter)
                } else { // clear current temporary epc masks.
                    mReader?.clearFilter()
                }
            }
        }
    }
}