package com.youpon.home1.comm.tools;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.gson.Gson;
import com.youpon.home1.bean.Device;
import com.youpon.home1.bean.gsonBeas.GsonAllDevice;
import com.youpon.home1.bean.gsonBeas.GsonAllPanel;
import com.youpon.home1.bean.gsonBeas.GsonAllScene;
import com.youpon.home1.bean.Panel;
import com.youpon.home1.bean.Scenebean;
import com.youpon.home1.bean.SubDevice;
import com.youpon.home1.bean.Sensor;
import com.youpon.home1.bean.gsonBeas.GsonAllSensor;
import com.youpon.home1.bean.gsonBeas.Liandong;
import com.youpon.home1.bean.gsonBeas.Timer;
import com.youpon.home1.comm.App;
import com.youpon.home1.comm.base.EventData;
import com.youpon.home1.http.HttpManage;
import com.youpon.home1.manage.DeviceManage;
import com.youpon.home1.manage.PanelManage;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xutils.common.Callback;
import org.xutils.common.util.KeyValue;
import org.xutils.db.sqlite.WhereBuilder;
import org.xutils.ex.DbException;

import java.util.ArrayList;
import java.util.List;

import io.xlink.wifi.sdk.XDevice;

/**
 * Created by liuyun on 2017/4/12.
 */
public class DataParser {
    private static DataParser instance;
    private DataParser(){}
    public static DataParser getInstance(){
        if(instance==null){
            instance=new DataParser();
        }
        return instance;
    }

    public void parse(byte[] bytes, XDevice xDevice, final Handler handler){
        Message msg = Message.obtain();
        final int gateID=xDevice.getDeviceId();
//        final String s485=new BigInteger(1, bytes).toString(16).trim();
//        if(s485.startsWith("55aa55")){
//            new Thread(){
//                @Override
//                public void run() {
//                    parse485Message(gateID,s485,handler);
//                }
//            }.start();
//            msg.obj =s485;
//        }else {
            final String s = new String(bytes).trim();
            msg.obj = s;
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            parseMessage(gateID,s,handler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
    }

    private void parse485Message(int deviceid, String s485,Handler handler) throws Exception {
        if(s485.length()<20){
            return;
        }
        String id = s485.substring(8, 12);
        int dst =Integer.parseInt(s485.substring(12, 14),16) ;
        int src = Integer.parseInt(s485.substring(14, 16),16);
        String cluster=s485.substring(16,20);
//        Log.e("verything", "cluster:" + cluster + ",id:" + id + ",dst:" + dst + ",src:" + src + ",uuu:" + s485.substring(22, 26));
        switch (cluster) {
            case "0000":
                Log.e("verything", "cluster:" + cluster + ",id:" + id + ",dst:" + dst + ",src:" + src + ",uuu:" + s485.substring(22, 26));
                if ("0005".equals(s485.substring(22, 26))) {
                    int type = Integer.parseInt(s485.substring(33, 34));
                    SubDevice devisortdb = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).findFirst();
                    if (devisortdb == null) {
                       return;
                    } else {
                        devisortdb.setType(type);
                    }
                    devisortdb.setOnline(true);
                    try {
                        App.db.update(devisortdb);
                    } catch (DbException e) {
                        e.printStackTrace();
                    }
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                }else if ("0007".equals(s485.substring(22, 26))){
                    if("06".equals(s485.substring(28, 30))){
                        try {
                            App.db.update(SubDevice.class,WhereBuilder.b("id","=",id).and("gateway_id","=",deviceid),new KeyValue("online",false));
                        } catch (DbException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case "0005":
                synchronized (this) {
                    Scenebean sc = null;
                    if ("05".equals(s485.substring(20, 22))) {
                        id = s485.substring(4,8);
                        Panel panel = PanelManage.getInstance().getPanelById(id,deviceid);
                        if (panel == null) break;
                        String mac1 = panel.getMac();
                        sc = App.db.selector(Scenebean.class).where("panel_mac","=",mac1).and("groupId", "=", s485.substring(22, 26)).and("sceneId", "=", s485.substring(26, 28)).findFirst();
                        if (sc != null) {
//                            sc.synkData();
                            sc.setStatus(1);
                            App.db.update(Scenebean.class,WhereBuilder.b("panel_mac","=",mac1).and("groupId", "=", s485.substring(22, 26)),new KeyValue("status",0));
                            App.db.replace(sc);
                            EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                            EventBus.getDefault().post(new EventData(EventData.CODE_REFRESH_DEVICE, ""));
                        }
                        break;
                    }else if("07".equals(s485.substring(20,22))){
                        id=s485.substring(4,8);
                        Panel panel = PanelManage.getInstance().getPanelById(id,deviceid);
                        if (panel == null) break;
                        String mac1 = panel.getMac();
                        App.db.update(Scenebean.class,WhereBuilder.b("panel_mac","=",mac1).and("groupId", "=", s485.substring(22, 26)).and("sceneId","=",s485.substring(26, 28)),new KeyValue("status",0));
                        EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                        EventBus.getDefault().post(new EventData(EventData.CODE_REFRESH_DEVICE, ""));
                    }else if ("09".equals(s485.substring(20, 22))) {
                        id = s485.substring(4,8);
                        if("FFFF".equals(id)){
                            Message ms = Message.obtain();
                            ms.what = 2;
                            switch (s485.substring(22, 24)) {
                                case "00":
                                    ms.obj = "场景写入成功";
                                    break;
                                case "01":
                                    ms.obj = "场景写入无效";
                                    break;
                                case "02":
                                    ms.obj = "场景写入失败";
                                    break;
                            }
                            handler.sendMessage(ms);
                        }else {
                        String groupId = s485.substring(22, 26);
                        String sceneId = s485.substring(26, 28);
                        Panel panel = PanelManage.getInstance().getPanelById(id,deviceid);
                        if (panel == null) break;
                        String mac1 = panel.getMac();
                        List<Scenebean.ActionsBean> list = new ArrayList<>();
                        int count = Integer.parseInt(s485.substring(28, 30), 16);
                        Log.e("12位场景","groupId:"+groupId+" sceneId:"+sceneId+"count:"+count);
                        for (int i = 0; i < count; i++) {
                            int dstid = Integer.parseInt(s485.substring(30 + 14 * i, 32 + 14 * i), 16);
                            String clu = s485.substring(32 + 14 * i, 36 + 14 * i);
                            int val = Integer.parseInt(s485.substring(42 + 14 * i, 44 + 14 * i), 16);
                            Scenebean.ActionsBean actionsBean = new Scenebean.ActionsBean();
                            actionsBean.setVal(val);
                            actionsBean.setNwkid(id);
                            actionsBean.setNclu("0800".equals(clu) ? "0008" : "0006");
                            actionsBean.setDstid(dstid);
                            actionsBean.setMac(mac1);
                            Log.e("actionbean", actionsBean.toString());
                            if("0001".equals(groupId)){
                                if(dstid==4||dstid==5){
                                    list.add(actionsBean);
                                }
                                continue;
                            }
                            list.add(actionsBean);
                        }
//                    if (panel.getClas() == 5 || panel.getClas() == 6 || panel.getClas() == 7) {
//                        panel.getMap().put(sceneId, list);
//                        EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
//                        break;
//                    }
                        boolean Tag = false;
                        if ("0001".equals(groupId)) {
                            panel.getMap().put(sceneId, list);
                            EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                            break;
                        } else if ("0000".equals(groupId)&&(panel.getClas()==9||panel.getClas()==299)) {
                            sc = App.db.selector(Scenebean.class).where("panel_mac", "=", mac1).and("groupId", "=", groupId).and("sceneId", "=", sceneId).findFirst();
                            if (sc == null) {
                                Tag = true;
                                sc = new Scenebean();
                                sc.setGateway_id(deviceid);
                                sc.setPanel_mac(mac1);
                                sc.setGroupId(groupId);
                                sc.setSceneId(sceneId);
                            }
                            sc.setType(1);
                            sc.setGateway_id(deviceid);
                            sc.setGateway_type(1);
                            sc.setId(id);
                            sc.setAction(list);
                            if (!Tag) {
                                App.db.update(sc);
                                EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                            } else {
                                App.db.replace(sc);
                                HttpManage.getInstance().addSub(HttpManage.TYPE_SINGLE, HttpManage.SCENETABLE, new Gson().toJson(sc), new MyCallback() {
                                    @Override
                                    public void onSuc(String result) {
                                        Scenebean scenebean = new Gson().fromJson(result, Scenebean.class);
                                        try {
                                            App.db.replace(scenebean);
                                            EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                                        } catch (DbException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onFail(int code, String msg) {
                                        Log.e("code and msg", code + " msg:" + msg);
                                    }
                                });
                            }
                        }
                    }
                        break;
                    }else if("08".equals(s485.substring(20, 22))){
                        String status=s485.substring(22,24);
                        String groupId = s485.substring(24, 28);
                        String sceneId = s485.substring(28, 30);
                        Panel panel = PanelManage.getInstance().getPanelById(id,deviceid);
                        if (panel == null) break;
                        String mac1 = panel.getMac();
                        List<Scenebean.ActionsBean> list = new ArrayList<>();
                        int count = Integer.parseInt(s485.substring(30, 32), 16);
                        Log.e("12位场景","groupId:"+groupId+" sceneId:"+sceneId);
                        for (int i = 0; i < count; i++) {
                            int dstid = Integer.parseInt(s485.substring(32 + 14 * i, 34 + 14 * i), 16);
                            String clu = s485.substring(34 + 14 * i, 38 + 14 * i);
                            int val = Integer.parseInt(s485.substring(44 + 14 * i, 46 + 14 * i), 16);
                            Scenebean.ActionsBean actionsBean = new Scenebean.ActionsBean();
                            actionsBean.setVal(val);
                            actionsBean.setNwkid(id);
                            actionsBean.setNclu("0800".equals(clu) ? "0008" : "0006");
                            actionsBean.setDstid(dstid);
                            actionsBean.setMac(mac1);
                            Log.e("actionbean", actionsBean.toString());
                            if("0001".equals(groupId)){
                                if(dstid==4||dstid==5){
                                    list.add(actionsBean);
                                }
                                continue;
                            }
                            list.add(actionsBean);
                        }
//                    if (panel.getClas() == 5 || panel.getClas() == 6 || panel.getClas() == 7) {
//                        panel.getMap().put(sceneId, list);
//                        EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
//                        break;
//                    }
                        boolean Tag = false;
                        if ("0001".equals(groupId)) {
                            panel.getMap().put(sceneId, list);
                            EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                            break;
                        } else if ("0000".equals(groupId)&&(panel.getClas()==9||panel.getClas()==299)) {
                            sc = App.db.selector(Scenebean.class).where("panel_mac", "=", mac1).and("groupId", "=", groupId).and("sceneId", "=", sceneId).findFirst();
                            if (sc == null) {
                                Tag = true;
                                sc = new Scenebean();
                                sc.setGateway_id(deviceid);
                                sc.setPanel_mac(mac1);
                                sc.setGroupId(groupId);
                                sc.setSceneId(sceneId);
                            }
                            if("04".equals(status)){
                                sc.setType(2);
                            }else
                                sc.setType(1);
                            sc.setGateway_id(deviceid);
                            sc.setGateway_type(1);
                            sc.setId(id);
                            sc.setAction(list);
                            if (!Tag) {
                                App.db.update(sc);
                                EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                            } else {
                                App.db.replace(sc);
                                HttpManage.getInstance().addSub(HttpManage.TYPE_SINGLE, HttpManage.SCENETABLE, new Gson().toJson(sc), new MyCallback() {
                                    @Override
                                    public void onSuc(String result) {
                                        Scenebean scenebean = new Gson().fromJson(result, Scenebean.class);
                                        try {
                                            App.db.replace(scenebean);
                                            EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE, ""));
                                        } catch (DbException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onFail(int code, String msg) {
                                        Log.e("code and msg", code + " msg:" + msg);
                                    }
                                });
                            }
                        }
                    }
                    }
                break;
            case "0006":
                String cons = s485.substring(20, 22);
                int tap;
                if ("04".equals(cons)) {
                    if(s485.substring(22,24).equals("86")){
                        Message massge = Message.obtain();
                        massge.what=2;
                        massge.obj="命令错误";
                        handler.sendMessage(massge);
                        return;
                    }
                    tap = Integer.parseInt(s485.substring(30, 32), 16);
                }else if("03".equals(cons)){
                    tap = Integer.parseInt(s485.substring(30, 32), 16);
                    src=Integer.parseInt(s485.substring(22, 24), 16);
                } else if("02".equals(cons)){
                    tap = Integer.parseInt(s485.substring(28, 30), 16);
                    src=Integer.parseInt(s485.substring(12, 14), 16);
                    id=s485.substring(4, 8);
                }else break;
                try {
                    SubDevice dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",src).findFirst();
                        if (dbdevi == null) {
                            return;
                        }
                        if(dbdevi.getTp()==1){
                            dbdevi.setValue2(tap);
                        }else
                        dbdevi.setValue1(tap);
                    dbdevi.setOnline(true);
                    App.db.update(dbdevi,"value1","value2","online");
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
//                    EventBus.getDefault().post(new EventData(EventData.CODE_READ_STUTAS, ""));
                } catch (DbException e) {
                    e.printStackTrace();
                }
                break;
            case "0008":
                cons = s485.substring(20, 22);
                int sst=Integer.parseInt(s485.substring(15,16));
                tap = 0;
                if ("04".equals(cons)) {
                    if(s485.substring(22,24).equals("86")){
                        Message massge = Message.obtain();
                        massge.what=2;
                        massge.obj="命令错误";
                        handler.sendMessage(massge);
                        return;
                    }
                    tap = Integer.parseInt(s485.substring(30,32), 16);
                }else if("02".equals(cons)){
                    tap = Integer.parseInt(s485.substring(28, 30), 16);
                    sst=Integer.parseInt(s485.substring(12, 14), 16);
                    id=s485.substring(4, 8);
                } else if ("0A".equals(cons)) {
                    tap = Integer.parseInt(s485.substring(28, 30), 16);
                } else if ("0B".equals(cons)) {
                    break;
                } else if("01".equals(cons)){
                    if("0016".equals(s485.substring(22,26))){
                        tap = Integer.parseInt(s485.substring(30,32), 16);
                        Log.e("换气档位","tap:"+tap);
                        App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",sst),new KeyValue("value2",tap));
                        EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                    }
                    break;
                }else if ("08".equals(cons)){
                    sst=Integer.parseInt(s485.substring(13,14));
                    id=s485.substring(4,8);
                    tap = Integer.parseInt(s485.substring(22,24), 16);
//                    Log.e("换气设备切换","tap:"+tap);
                    App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",sst),new KeyValue("value2",tap));
//                    Log.e("换气设备切换","subdevice:"+App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",sst).findFirst());
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                    break;
                }
                try {
                    SubDevice dbdevi;
                    if(sst==2){
                        dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",1).findFirst();
                        if (dbdevi == null) {
                            return;
                        }
                        dbdevi.setValue2(tap);
                    }else {
                        dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",sst).findFirst();
                        if (dbdevi == null) {
                            return;
                        }
                        dbdevi.setValue1(tap);
                    }
                    dbdevi.setOnline(true);
//                    Log.e("dededde",dbdevi.toString());
                    App.db.update(dbdevi,"value1","value2","online");
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                } catch (DbException e) {
                    e.printStackTrace();
                }
                break;
            case "FC00":
                /*
                        typedef enum
                        {
	                     SENSOR_TYPE_IR =1,//红外,簇ID 0x0406,属性ID为 0
	                     SENSOR_TYPE_BEAM=2,//光感, 簇ID 0x0400, 属性ID为 0
	                     SENSOR_TYPE_TEMPR=3,//温度, 簇ID 0x0402, 属性ID为 0
	                     SENSOR_TYPE_HUMIDITY=4,//湿度,簇ID 0x0405, 属性ID为 0
	                     SENSOR_TYPE_CO2=5,//二氧化碳,簇ID 0xFC01, 属性ID为 0
	                     SENSOR_TYPE_TVOC=6,//刺激性气味,簇ID 0xFC01, 属性ID为 1
	                     SENSOR_TYPE_COMBUSTIBLE=7,//可燃气,簇ID 0xFC01, 属性ID为 2
	                     SENSOR_TYPE_SMOKE=8,//烟感,簇ID 0xFC01, 属性ID为 3
	                     SENSOR_TYPE_MAX
                       } sensor_type_def_e;
                  */
//                src=Integer.parseInt(s485.substring(25,26));
                synchronized (this) {
//                    SubDevice SubDevice = null;
//                    try {
//                        SubDevice = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst", "=",src).findFirst();
//                    } catch (DbException e) {
//                        e.printStackTrace();
//                    }
//                    if (SubDevice == null) {
//                        return;
//                    }
                    if("03".equals(s485.substring(20,22))&&"0000".equals(id)){
                        App.db.update(SubDevice.class,WhereBuilder.b("gateway_id","=",deviceid).and("clas","=",299).and("type","!=",2),new KeyValue[]{new KeyValue("value1",0),new KeyValue("value2",0)});
                        App.db.update(SubDevice.class,WhereBuilder.b("gateway_id","=",deviceid).and("clas","=",299).and("type","=",2),new KeyValue("value2",0));
                    }
                    int index=25;
                    while (index+13<=s485.length()){
                        Log.e("while",index+"");
                        String clu=s485.substring(index+1,index+5);
                        switch (clu){
                            case "0008":
                            case "0006":
                                int ddst=Integer.parseInt(s485.substring(index,index+1));
                                int ttap=Integer.parseInt(s485.substring(index+11,index+13),16);
                                Log.e("S485","clu:"+clu);
                                Log.e("S485","ttap:"+ttap);
                                Log.e("S485","id:"+id);
                                Log.e("S485","dst:"+ddst);
                                index=index+14;
                                try {
                                    if(ddst==2&&"0008".equals(clu)){
                                        App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id", "=", deviceid).and("dst", "=",1),new KeyValue("value2",ttap));
                                    }else{
                                        SubDevice dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",ddst).findFirst();
                                        if (dbdevi == null) {
                                            break;
                                        }
                                        if(dbdevi.getTp()==1&&"0006".equals(clu)){
                                            dbdevi.setValue2(ttap);
                                        }else
                                            dbdevi.setValue1(ttap);
                                        dbdevi.setOnline(true);
                                        Log.e("dbdevi",dbdevi.toString());
                                        App.db.update(dbdevi,"value1","value2","online");
                                    }
                                } catch (DbException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case "FC01":
                                try {
                                    int i = Integer.parseInt(s485.substring(index + 8, index + 9));
                                int type=5+i;
                                int ttap1=Integer.parseInt(s485.substring(index+11,index+15),16);
                                Log.e("S485","clu:"+clu+",type:"+type);
                                Log.e("S485","ttap:"+ttap1);
                                    App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("device_id","=",deviceid).and("type","=",type),new KeyValue[]{new KeyValue("value1",ttap1),new KeyValue("online",true)});
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                index=index+16;
                                break;
                            case "0402":
                            case "0405":
                                int tp=3;
                                int tap2=Integer.parseInt(s485.substring(index+11,index+15),16);
                                if(cluster.equals("0402")){
                                    tap2=(tap2&0x8000)>0?(tap2-0x10000):tap2;
                                }
                                tap2/=100;
                                Log.e("S485","clu:"+clu);
                                Log.e("S485","ttap:"+tap2);
                                try {
                                    Log.e("UUUUJJJ","id:"+id+" device_id:"+deviceid+" type:"+tp);
//                                    Sensor first = App.db.selector(Sensor.class).where("devisort_id", "=", id).and("device_id", "=", deviceid).and("type", "=", ty).findFirst();
//                                    if (first==null){
//                                        Log.e("UUUU","sensor为空");
//                                        return;
//                                    }else first.setValue1(ttap2);
//                                    App.db.replace(first);
                                    if("0405".equals(clu)){
                                        App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("device_id","=",deviceid).and("type","=",tp),new KeyValue[]{new KeyValue("value2",tap2),new KeyValue("online",true)});
                                    }else
                                    App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("device_id","=",deviceid).and("type","=",tp),new KeyValue[]{new KeyValue("value1",tap2),new KeyValue("online",true)});
                                } catch (DbException e) {
                                    e.printStackTrace();
                                }
                                index=index+16;
                                break;
                            case "0403":
                                index=index+16;
                                break;
                            case "0400":
                                int ty=2;
                                int ttap2=Integer.parseInt(s485.substring(index+11,index+15),16);
                                Log.e("S485","clu:"+clu);
                                Log.e("S485","ttap:"+ttap2);
                                try {
                                    Log.e("UUUUJJJ","id:"+id+" device_id:"+deviceid+" type:"+ty);
//                                    Sensor first = App.db.selector(Sensor.class).where("devisort_id", "=", id).and("device_id", "=", deviceid).and("type", "=", ty).findFirst();
//                                    if (first==null){
//                                        Log.e("UUUU","sensor为空");
//                                        return;
//                                    }else first.setValue1(ttap2);
//                                    App.db.replace(first);
                                    App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("device_id","=",deviceid).and("type","=",ty),new KeyValue[]{new KeyValue("value1",ttap2),new KeyValue("online",true)});
                                } catch (DbException e) {
                                    e.printStackTrace();
                                }
                                index=index+16;
                                break;
                            case "0406":
                                int typ=1;
                                int ttap3=Integer.parseInt(s485.substring(index+11,index+13),16);
                                Log.e("S485","clu:"+clu);
                                Log.e("S485","ttap:"+ttap3);
                                try {
                                    App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("device_id","=",deviceid).and("type","=",typ),new KeyValue[]{new KeyValue("value1",ttap3),new KeyValue("online",true)});
                                } catch (DbException e) {
                                    e.printStackTrace();
                                }
                                index=index+14;
                                break;
                            default:
                                index++;
                                break;
                        }
                    }
//                    for (int i = 0; i < kinds; i++) {
//                        int ddst=Integer.parseInt(s485.substring(25+14*i,26+14*i));
//                        int ttap=Integer.parseInt(s485.substring(36+14*i,38+14*i),16);
//                        Log.e("S485","ddst:"+ddst);
//                        Log.e("S485","ttap:"+ttap);
//                        try {
//                            if(ddst==2){
//                             App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("dst", "=",1),new KeyValue("value2",ttap));
//                            }else
//                             App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("dst", "=", ddst),new KeyValue("value1",ttap));
//                        } catch (DbException e) {
//                            e.printStackTrace();
//                        }
//                    }
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_SENSOR, "刷新传感器"));
                }
                break;
        }
    }



    private void parseMessage(int deviceid,String s,Handler myhandle) throws Exception {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(s);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(jsonObject==null)
            return;
        if(s.contains("\"CMD\":100")){
             synchronized (this){
                 JSONArray devices = jsonObject.optJSONArray("devices");
            List<SubDevice> list=new ArrayList<>();
             List<Panel> list1=new ArrayList<>();
            for (int i = 0; i < devices.length(); i++) {
                JSONObject jo = devices.optJSONObject(i);
                GsonAllDevice gsonAllDevice=null;
                try {
                    gsonAllDevice = new Gson().fromJson(jo.toString(), GsonAllDevice.class);
                }catch (Exception e){
                    e.printStackTrace();
                }
                if(gsonAllDevice==null){
                    return;
                }
                List<GsonAllDevice.EndpBean> endp = gsonAllDevice.getEndp();
                boolean flag1=false;
                try {
                    Panel panel= App.db.selector(Panel.class).where("mac", "=",gsonAllDevice.getMac()).findFirst();
                    if(panel==null){
                        panel=new Panel();
                        flag1=true;
                    }
                    panel.setGateway_id(deviceid);
                    panel.setGateway_type(gsonAllDevice.getRiu());
                    panel.setClas(gsonAllDevice.getClassX());
                    panel.setMac(gsonAllDevice.getMac());
                    panel.setId(gsonAllDevice.getNwkid());
                    panel.setOnline(gsonAllDevice.getOnline()==1?true:false);
                    if(flag1){
                        list1.add(panel);
                    }
                    App.db.replace(panel);
                } catch (DbException e) {
                    e.printStackTrace();
                }
                if(list1.size()>0)
                HttpManage.getInstance().addSub(HttpManage.TYPE_MORE,"panel", new Gson().toJson(list1), new Callback.CommonCallback<String>() {
                         @Override
                         public void onSuccess(String result) {
                             Log.e("HTTT","上传面板数据成功"+result);
                             try {
                                 JSONArray jsonArray = new JSONArray(result);
                                 for (int i = 0; i < jsonArray.length(); i++) {
                                     JSONObject jsonObject1 = jsonArray.optJSONObject(i);
                                     Panel panel = new Gson().fromJson(jsonObject1.toString(), Panel.class);
                                     PanelManage.getInstance().addPanel(panel);
                                 }
                             } catch (JSONException e) {
                                 e.printStackTrace();
                             }
                         }

                         @Override
                         public void onError(Throwable ex, boolean isOnCallback) {
                             ex.printStackTrace();
                         }

                         @Override
                         public void onCancelled(CancelledException cex) {

                         }

                         @Override
                         public void onFinished() {
                             Log.e("HTTT","上传面板数据完成");
                         }
                     });
                for (int j = 0; j < endp.size(); j++) {
                    boolean flag=false;
                    GsonAllDevice.EndpBean endpBean = endp.get(j);
                    int dst=endpBean.getDstid();
                    if (endpBean.getT()>10){
                        break;
                    }
                try {
                    if(endpBean.getDstid()==2&&endpBean.getT()==1){
                        dst=1;
                    }
                    SubDevice subDe = App.db.selector(SubDevice.class).where("mac", "=", gsonAllDevice.getMac()).and("dst","=",dst).findFirst();
                    if(subDe==null){
                        subDe=new SubDevice();
                        flag=true;
                    }
                    subDe.setClas(gsonAllDevice.getClassX());
                    subDe.setDst(dst);
                    subDe.setMac(gsonAllDevice.getMac());
                    subDe.setId(gsonAllDevice.getNwkid());
                    subDe.setOnline(gsonAllDevice.getOnline()==1?true:false);
                    subDe.setGateway_id(deviceid);
                    subDe.setGateway_type(gsonAllDevice.getRiu());
                    subDe.setMyType(endpBean.getT());
                    if(endpBean.getDstid()==2&&endpBean.getT()==1){
                        subDe.setValue2(endpBean.getVal());
                    }else
                    subDe.setValue1(endpBean.getVal());
                    if(flag){
                        list.add(subDe);
                    }
                    App.db.replace(subDe);
                    if(subDe.getType()==4){
                        Command.sendData1(deviceid,Command.getOtherStr("55AA55000F0100"+gsonAllDevice.getNwkid()+"000006010008000016FF",0).getBytes(),"detaparser");
                    }
                } catch (DbException e) {
                    e.printStackTrace();
                }
                }
            }
             EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,""));
            if(list.size()>0)
            HttpManage.getInstance().addSub(HttpManage.TYPE_MORE,"subdevice", new Gson().toJson(list), new Callback.CommonCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Log.e("HTTT","上传子设备数据成功"+result);
                    try {
                        JSONArray jsonArray = new JSONArray(result);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject1 = jsonArray.optJSONObject(i);
                            SubDevice subDevice = new Gson().fromJson(jsonObject1.toString(), SubDevice.class);
                            App.db.replace(subDevice);
                            EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,""));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (DbException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Throwable ex, boolean isOnCallback) {
                    ex.printStackTrace();
                }

                @Override
                public void onCancelled(CancelledException cex) {

                }

                @Override
                public void onFinished() {
                    Log.e("HTTT","上传子设备数据完成");
                }
            });
            return;
        }
        }else if(s.contains("\"CMD\":110")){
//            synchronized (this){
//                JSONArray devices = jsonObject.optJSONArray("devices");
//                List<Panel> list=new ArrayList<>();
//                for (int i = 0; i < devices.length(); i++) {
//                    JSONObject jo = devices.optJSONObject(i);
//                    GsonAllPanel gsonAllPanel = new Gson().fromJson(jo.toString(), GsonAllPanel.class);
//                    List<GsonAllPanel.ChnlBean> chnl = gsonAllPanel.getChnl();
//                    boolean flag=false;
//                    try {
//                         Panel panel= App.db.selector(Panel.class).where("mac", "=",gsonAllPanel.getMac()).findFirst();
//                         if(panel==null){
//                                panel=new Panel();
//                                flag=true;
//                         }
//                            panel.setGateway_id(deviceid);
//                            panel.setGateway_type(gsonAllPanel.getRiu());
//                            panel.setClas(gsonAllPanel.getClassX());
//                            panel.setMac(gsonAllPanel.getMac());
//                            panel.setId(gsonAllPanel.getNwkid());
//                            panel.setOnline(gsonAllPanel.getOnline()==1?true:false);
//                            panel.setChnls(new Gson().toJson(chnl));
//                            if(flag){
//                                list.add(panel);
//                            }
//                            App.db.replace(panel);
//                        } catch (DbException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                if(list.size()>0)
//                    HttpManage.getInstance().addSub(HttpManage.TYPE_MORE,"panel", new Gson().toJson(list), new Callback.CommonCallback<String>() {
//                        @Override
//                        public void onSuccess(String result) {
//                            Log.e("HTTT","上传面板数据成功"+result);
//                            try {
//                                JSONArray jsonArray = new JSONArray(result);
//                                for (int i = 0; i < jsonArray.length(); i++) {
//                                    JSONObject jsonObject1 = jsonArray.optJSONObject(i);
//                                    Panel panel = new Gson().fromJson(jsonObject1.toString(), Panel.class);
//                                    PanelManage.getInstance().addPanel(panel);
//                                }
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                            }
//                        }
//
//                        @Override
//                        public void onError(Throwable ex, boolean isOnCallback) {
//                            ex.printStackTrace();
//                        }
//
//                        @Override
//                        public void onCancelled(CancelledException cex) {
//
//                        }
//
//                        @Override
//                        public void onFinished() {
//                            Log.e("HTTT","上传面板数据完成");
//                        }
//                    });
//                return;
//            }
        }else if(s.contains("\"CMD\":113")){
            synchronized (this){
                String result = jsonObject.optString("result");
                Message msg= Message.obtain();
                msg.what=2;
                msg.obj=result;
                myhandle.sendMessage(msg);
            }
        }else if(s.contains("\"CMD\":111")){//传感器
            synchronized (this){
                JSONArray devices = jsonObject.optJSONArray("devices");
                List<Sensor> list=new ArrayList<>();
                for (int i = 0; i < devices.length(); i++) {
                    JSONObject jo = devices.optJSONObject(i);
                    GsonAllSensor gsonAllSensor=null;
                    try {
                        gsonAllSensor = new Gson().fromJson(jo.toString(), GsonAllSensor.class);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    if(gsonAllSensor==null){
                        return;
                    }
                    List<GsonAllSensor.ChnlBean> chnl = gsonAllSensor.getChnl();
                    for (int j = 0; j < chnl.size(); j++) {
                        boolean flag=false;
                        GsonAllSensor.ChnlBean chnlBean = chnl.get(j);
                        int type=chnlBean.getType();
                        Log.e("Sensor","device_id:"+deviceid+" mac:"+gsonAllSensor.getMac()+" nwkid:"+gsonAllSensor.getNwkid()+" type:"+type);
                        try {
                            Sensor sensor = App.db.selector(Sensor.class).where("mac", "=", gsonAllSensor.getMac()).and("type","=",type==4?3:type).findFirst();
                            if(sensor==null){
                                sensor=new Sensor();
                                flag=true;
                            }
                            sensor.setOnline(gsonAllSensor.getOnline()==1?true:false);
                            sensor.setDevice_id(deviceid);
                            sensor.setDevisort_id(gsonAllSensor.getNwkid());
                            sensor.setMac(gsonAllSensor.getMac());
                            sensor.setMytype(type==4?3:type);
                            if(type==4){
                                sensor.setValue2(chnlBean.getVal());
                            }else
                            sensor.setValue1(chnlBean.getVal());
                            if(flag){
                                list.add(sensor);
                            }
                            App.db.replace(sensor);
                        } catch (DbException e) {
                            e.printStackTrace();
                        }
                    }
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,""));
                if(list.size()>0)
                    HttpManage.getInstance().addSub(HttpManage.TYPE_MORE,"Dbsensor", new Gson().toJson(list), new Callback.CommonCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Log.e("HTTT","上传传感器数据成功"+result);
                            try {
                                JSONArray jsonArray = new JSONArray(result);
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject jsonObject1 = jsonArray.optJSONObject(i);
                                    Sensor sensor = new Gson().fromJson(jsonObject1.toString(), Sensor.class);
                                    App.db.replace(sensor);
                                    EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,""));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (DbException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(Throwable ex, boolean isOnCallback) {
                            ex.printStackTrace();
                        }

                        @Override
                        public void onCancelled(CancelledException cex) {

                        }

                        @Override
                        public void onFinished() {
                            Log.e("HTTT","上传传感器数据完成");
                        }
                    });
                return;
            }
        }else if(s.contains("\"CMD\":151")){
            synchronized (this){
                JSONObject list = jsonObject.optJSONObject("list");
                    Liandong liandong = new Gson().fromJson(list.toString(), Liandong.class);
                    Liandong.getMap().put(liandong.getCtrl_id(),liandong);
                EventBus.getDefault().post(new EventData(EventData.CODE_REFRESHLINK,""));
                return;
            }
        }else if(s.contains("\"CMD\":146")){
            synchronized (this){
                JSONArray list = jsonObject.optJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject js = list.optJSONObject(i);
                    Liandong liandong = new Gson().fromJson(js.toString(), Liandong.class);
                    Liandong.getMap().put(liandong.getCtrl_id(),liandong);
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_REFRESHLINK,""));
                return;
            }
        }else if(s.contains("\"CMD\":139")){
            synchronized (this){
                JSONObject list = jsonObject.optJSONObject("list");
                Timer timer = new Gson().fromJson(list.toString(), Timer.class);
                try {
                    App.db.replace(timer);
                } catch (DbException e) {
                    e.printStackTrace();
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_REFRESH_TASK,""));
                return;
            }
        }
        final int t = jsonObject.optInt("t");
        final String ids = jsonObject.optString("ids");
        final String pay = jsonObject.optString("pay");
        final String nId = jsonObject.optString("nId");
        Message massge = Message.obtain();
        massge.what=2;
        switch (t){
            case 15:
                String s485 = jsonObject.optString("rs485");
                if(s485.length()>0)
                parse485Message(deviceid,s485,myhandle);
                break;
            case 2:
                String id = ids.substring(0, 4);
                int dst=Integer.parseInt(ids.substring(ids.length()-1));
                String cluster=ids.substring(8,12);
                switch (cluster){
                    case "0006":
                    case "0008":
                    case "0000":
                        if(pay.contains("0A050042")){
//                            synchronized (this) {
//                                SubDevice SubDevice = null;
//                                try {
//                                    SubDevice = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id", "=", deviceid).and("dst","=",1).findFirst();
//                                } catch (DbException e) {
//                                    e.printStackTrace();
//                                }
//                                Log.e("NNNNNNNNNNN", pay);
//                                int type = Integer.parseInt(pay.substring(pay.length() - 1));
//                                Log.e("MMMMMMMMMM", type + "");
//                                if (SubDevice == null) {
//                                    SubDevice = new SubDevice();
//                                    SubDevice.setId(id);
//                                    SubDevice.setType(type);
//                                    SubDevice.setMac(id + type);
//                                    SubDevice.setGateway_id(deviceid);
//                                    try {
//                                        App.db.replace(SubDevice);
//                                    } catch (DbException e) {
//                                        e.printStackTrace();
//                                    }
//                                } else {
//                                    try {
//                                        Log.e("SubDevice",SubDevice.toString());
//                                        SubDevice.setType(type);
//                                        Log.e("NAME1",SubDevice.getId()+";"+SubDevice.getName());
//                                        SubDevice.setMyDst(1);
//                                        SubDevice.setMac(SubDevice.getMac());
//                                        Log.e("NAME2",SubDevice.getId()+";"+SubDevice.getName());
//                                        App.db.replace(SubDevice);
//                                        if (type == 5) {
//                                            for (int i = 3; i < 7; i++) {
//                                                long count = App.db.selector(SubDevice.class).where("id", "=", id).and("dst", "=", i).and("gateway_id", "=", deviceid).count();
//                                                if (count == 0) {
//                                                    SubDevice.setName("");
//                                                    SubDevice.setMyDst(i);
//                                                    SubDevice.setMac(SubDevice.getMac());
//                                                    App.db.replace(SubDevice);
//                                                }
//                                            }
//                                        } else if (type == 2) {
//                                            SubDevice.setDst(2);
//                                            SubDevice.setMac(SubDevice.getMac());
//                                            App.db.replace(SubDevice);
//                                        }
//                                    } catch (DbException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE, "刷新子设备"));
//                            }
                        }else if(pay.contains("0A0000")||pay.contains("01000000")){
                              int tap =Integer.parseInt(pay.substring(pay.length()-2),16);
                            try {
                                SubDevice dbde = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id","=",deviceid).findFirst();
                                if(dbde==null){
                                    return;
                                }else if(dbde.getClas()==6||dbde.getClas()==7){
                                    SubDevice dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst).findFirst();
                                    if(dbdevi==null){
                                        return;
                                    }else {
                                        App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst),new KeyValue[]{new KeyValue("value1",tap),new KeyValue("online",true)});
                                        EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE,"刷新子设备"));
                                        return;
                                    }
                                }else {
                                    SubDevice dbdevi = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst==2?1:dst).findFirst();
                                    if(dbdevi==null){
                                        return;
                                    }
                                    if(dst==3&&"0006".equals(cluster)||dst==2){
                                        App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst==2?1:dst),new KeyValue[]{new KeyValue("value2",tap),new KeyValue("online",true)});
                                    }else
                                        App.db.update(SubDevice.class,WhereBuilder.b("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst),new KeyValue[]{new KeyValue("value1",tap),new KeyValue("online",true)});
                                    EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_DEVICE,"刷新子设备"));
                                }
                            } catch (DbException e) {
                                e.printStackTrace();
                            }
                        }
//                        else if(pay.contains("0B0400")||pay.contains("0B0000")){
//                            sendCommand(deviceid,devicepw,Command.getOtherStr(Command.FRESH));
//                            if("0006".equals(cluster)){
//                                sendCommand(deviceid,devicepw,Command.getReadString(6,0,id,1,dst));
//                            }else
//                            sendCommand(deviceid,devicepw,Command.getReadString(8,0,id,1,dst));
//                        }else if(pay.contains("0B0100")){
//                            sendCommand(deviceid,devicepw,Command.getReadString(6,0,id,1,dst));
//                            sendCommand(deviceid,devicepw,Command.getReadString(8,0,id,1,dst));
//                        }
//                        else if(pay.contains("01000000")){
//                            int tap =Integer.parseInt(pay.substring(pay.length()-2),16);
//                            SubDevice de = null;
//                            try {
//                                de = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id","=",deviceid).and("dst","=",dst==2?1:dst).findFirst();
//                                if(de==null){
//                                   return;
//                                }
//                                if(dst==3&&"0006".equals(cluster)||dst==2){
//                                    de.setValue2(tap);
//                                }else de.setValue1(tap);
//                                de.setOnline(true);
//                                App.db.replace(de);
//                                EventBus.getDefault().post(new EventData(EventData.CODE_READ_STUTAS,""));
//                            } catch (DbException e) {
//                                e.printStackTrace();
//                            }
//                        }
                        break;
                    case "FC01":
                        int v=Integer.parseInt(pay.substring(12,16),16);
                        int ty = Integer.parseInt(pay.substring(7,8));
                        int type=ty+5;
                        if(v>10000)
                            break;
                        Log.e("sensor:","type:"+type+"  value:"+v);
                        try {
                            App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("type","=",type).and("device_id","=",deviceid),new KeyValue[]{new KeyValue("online",true),new KeyValue("value1",v)});
                        } catch (DbException e) {
                            e.printStackTrace();
                        }
                        EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_SENSOR,"刷新传感器"));
                        break;
                    case "0402":
                    case "0403":
                    case "0400":
                    case "0405":
                    case "0406":
                        int value=0;
                        if("0406".equals(cluster)){
                            value=Integer.parseInt(pay.substring(12,14),16);
                        }else
                            value=Integer.parseInt(pay.substring(12,16),16);
                        int type1 = Integer.parseInt(cluster.substring(3));
                        try {

                        if("0400".equals(cluster)){
                            type1=2;
                        }else if("0402".equals(cluster)){
                            type1=3;
                            value/=100;
                        }else if("0405".equals(cluster)){
                            type1=3;
                            value/=100;
                            App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("type","=",type1).and("device_id","=",deviceid),new KeyValue[]{new KeyValue("online",true),new KeyValue("value2",value)});
                        }else if("0406".equals(cluster)){
                            type1=1;
                        }
//                        if(cluster.equals("0402")||cluster.equals("0403")){
//                            value=(value&0x8000)>0?(value-0x10000):value;
//                        }
//                        Sensor dbsensor1 = null;

//                            if("0402".equals(cluster))
                            if(!"0405".equals(cluster))
                            App.db.update(Sensor.class,WhereBuilder.b("devisort_id","=",id).and("type","=",type1).and("device_id","=",deviceid),new KeyValue[]{new KeyValue("online",true),new KeyValue("value1",value)});
//                            dbsensor1 = App.db.selector(Sensor.class).where("id", "=", id +type).findFirst();
//                            if(dbsensor1==null){
//                                dbsensor1 = new Sensor(deviceid, id,type,value,0);
//                            }else
//                                dbsensor1.setValue1(value);
//                            dbsensor1.setOnline(true);
//                            App.db.replace(dbsensor1);
                        } catch (DbException e) {
                            e.printStackTrace();
                        }
                        EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_SENSOR,"刷新传感器"));
                        break;
//                    case "0406":
//                        String svalue1=pay.substring(12, 14);
//                        int value1=Integer.parseInt(svalue1,16);
//                        int type1 = Integer.parseInt(cluster.substring(3));
//                        Sensor dbsensor2 = null;
//                        try {
//                            dbsensor2 = App.db.selector(Sensor.class).where("id", "=", id +type1).findFirst();
//                            if(dbsensor2==null){
//                                dbsensor2 = new Sensor(deviceid, id,type1,value1,0);
//                            }else{
//                                dbsensor2.setValue1(value1);
//                            }
//                            dbsensor2.setOnline(true);
//                            App.db.replace(dbsensor2);
//                        } catch (DbException e) {
//                            e.printStackTrace();
//                        }
//                        EventBus.getDefault().postSticky(new EventData(EventData.CODE_REFRESH_SENSOR,"刷新传感器"));
//                        break;
                    case "0005":
                        String commandId = pay.substring(8, 10);
                        if("09".equals(commandId)){
                            String status = pay.substring(10, 12);
                            Message ms = Message.obtain();
                            ms.what = 2;
                            switch (status) {
                                case "00":
                                    ms.obj = "场景写入成功";
                                    break;
                                case "01":
                                    ms.obj = "场景写入无效";
                                    break;
                                case "02":
                                    ms.obj = "场景写入失败";
                                    break;
                            }
                            myhandle.sendMessage(ms);
                            break;
                        }else if("08".equals(commandId)){
                            String status=pay.substring(10,12);
                            String groupId=pay.substring(12,16);
                            String sceneId=pay.substring(16,18);
                            int count=Integer.parseInt(pay.substring(18,20),16);
                            Panel panel = PanelManage.getInstance().getPanelById(id,deviceid);
                            if (panel==null)break;
                            String mac1 = panel.getMac();
                            List<Scenebean.ActionsBean> list=new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                int dstid=Integer.parseInt(pay.substring(20+14*i,22+14*i),16);
                                String clu=pay.substring(22+14*i,26+14*i);
                                int val=Integer.parseInt(pay.substring(32+14*i,34+14*i),16);
                                Scenebean.ActionsBean actionsBean = new Scenebean.ActionsBean();
                                actionsBean.setVal(val);
                                actionsBean.setNwkid(id);
                                actionsBean.setNclu("0800".equals(clu)?"0008":"0006");
                                actionsBean.setDstid(dstid);
                                actionsBean.setMac(mac1);
                                if("0001".equals(groupId)){
                                    if(dstid==4||dstid==5){
                                        list.add(actionsBean);
                                    }
                                    continue;
                                }
                                list.add(actionsBean);
                            }
                            Log.e("class:",panel.getClas()+" groupId:"+groupId+"scenenid:"+sceneId);
                            if ("0100".equals(groupId)||"0001".equals(groupId)){
                                panel.getMap().put(sceneId,list);
                                EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE,""));
                                break;
                            }else if("0000".equals(groupId)&&(panel.getClas()==9||panel.getClas()==299)){
                                Scenebean sc = App.db.selector(Scenebean.class).where("panel_mac", "=", mac1).and("groupId", "=", groupId).and("sceneId", "=", sceneId).findFirst();
                                boolean Tag=false;
                                if(sc==null){
                                    Tag=true;
                                    sc=new Scenebean();
                                    sc.setPanel_mac(mac1);
                                    sc.setGroupId(groupId);
                                    sc.setSceneId(sceneId);
                                    if("04".equals(status)){
                                        sc.setType(2);
                                    }else
                                        sc.setType(1);
                                }
                                sc.setGateway_id(deviceid);
                                sc.setGateway_type(0);
                                sc.setId(id);
                                sc.setAction(list);
                                if(!Tag){
                                    App.db.update(sc);
                                    EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE,""));
                                }
                                if(Tag){
                                    App.db.replace(sc);
                                    HttpManage.getInstance().addSub(HttpManage.TYPE_SINGLE, HttpManage.SCENETABLE, new Gson().toJson(sc), new MyCallback() {
                                        @Override
                                        public void onSuc(String result) {
                                            Scenebean scenebean = new Gson().fromJson(result, Scenebean.class);
                                            try {
                                                App.db.replace(scenebean);
                                                EventBus.getDefault().post(new EventData(EventData.CODE_GETSCENE,""));
                                            } catch (DbException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                        @Override
                                        public void onFail(int code, String msg) {
                                            Log.e("code and msg",code+" msg:"+msg);
                                        }
                                    });
                                }
                            }
                        }
                        break;
                    case "0013":
                        String mac=pay.substring(6,pay.length()-2);
                        try {
                            synchronized (this){
                                List<SubDevice> SubDeviceList = App.db.selector(SubDevice.class).where("mac", "=", mac).findAll();
                                if(SubDeviceList!=null&&SubDeviceList.size()!=0){
                                    App.db.update(SubDevice.class, WhereBuilder.b("mac","=",mac),new KeyValue("id",id));
                                }
//                                    else {
//                                    List<SubDevice> SubDevices = App.db.selector(SubDevice.class).where("id", "=", id).and("gateway_id","=",deviceid).findAll();
//                                    if(SubDevices==null||SubDevices.size()==0){
//                                        SubDevice SubDevice = new SubDevice();
//                                        SubDevice.setId(id);
//                                        SubDevice.setMac(mac);
//                                        SubDevice.setGateway_id(deviceid);
//                                        App.db.replace(SubDevice);
//                                        Log.e("SubDevice",SubDevice.toString());
//                                    }else {
//                                        if(SubDevices.get(0).getType()==5){
//                                            SubDevice SubDevice = SubDevices.get(0);
//                                            App.db.delete(SubDevices);
//                                            Log.e("DSt",SubDevice.toString());
//                                            SubDevice.setMyType(1);
//                                            Log.e("DSt1",SubDevice.toString());
//                                            SubDevice.setMac(mac);
//                                            App.db.replace(SubDevice);
//                                            for (int i = 3; i < 7; i++) {
//                                                SubDevice first = App.db.selector(SubDevice.class).where("id", "=", id).and("dst", "=", i).and("gateway_id", "=", deviceid).findFirst();
//                                                if (first == null) {
//                                                    SubDevice.setName("");
//                                                    SubDevice.setMyType(i);
//                                                    Log.e("DSt2",SubDevice.toString());
//                                                    SubDevice.setMac(mac);
//                                                    App.db.replace(SubDevice);
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
                            }
                        } catch (DbException e) {
                            e.printStackTrace();
                        }
                }
                break;
            case 0:
//                massge.obj="命令无效";
//                myhandle.sendMessage(massge);
                break;
            case 3:
                try {
                        Device device = DeviceManage.getInstance().getDevice(deviceid);
                    if(device!=null){
                        device.setOnline(true);
                        DeviceManage.getInstance().addDevice(device);
                        App.db.update(SubDevice.class, WhereBuilder.b("id", "=", nId).and("gateway_id", "=", deviceid), new KeyValue("online", true));
                    }
                }
                catch (DbException e) {
                    e.printStackTrace();
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,"OK"));
                massge.obj="设备接入";
                myhandle.sendMessage(massge);
                break;
            case 10:
                if(jsonObject.optInt("nwkSta")==0){
                    try {
                        App.db.delete(SubDevice.class,WhereBuilder.b("id","=",nId).and("gateway_id","=",deviceid));
                        App.db.delete(Sensor.class,WhereBuilder.b("devisort_id","=",nId).and("device_id","=",deviceid));
                        Log.e("UUUUU","设备已删除");
                    } catch (DbException e) {
                        e.printStackTrace();
                    }
                    massge.obj="设备已退网";
                    myhandle.sendMessage(massge);
                }else{
                    try{
                    if(jsonObject.optInt("nwkSta")==3||jsonObject.optInt("nwkSta")==1) {
                        Device device = DeviceManage.getInstance().getDevice(deviceid);
                        if(device!=null){
                            device.setOnline(true);
                            DeviceManage.getInstance().addDevice(device);
                        }
                        App.db.update(SubDevice.class,WhereBuilder.b("id","=",nId).and("gateway_id","=",deviceid),new KeyValue("online",true));
                    }else if(jsonObject.optInt("nwkSta")==2){
//                        App.db.update(SubDevice.class,WhereBuilder.b("id","=",nId).and("gateway_id","=",deviceid),new KeyValue("online",false));
                    }else if(jsonObject.optInt("nwkSta")==4){
                        App.db.update(SubDevice.class,WhereBuilder.b("id","=",nId).and("gateway_id","=",deviceid),new KeyValue("online",false));
                    }
                }catch (DbException e) {
                        e.printStackTrace();
                    }
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_REFRESH_DEVICE,"OK"));
                EventBus.getDefault().post(new EventData(EventData.CODE_REFRESH_SENSOR,"OK"));
                break;
            case 12:
                String stkSta = jsonObject.optString("stkSta");
                if("90".equals(stkSta)){
                    massge.obj="网络就绪";
                }else
                    massge.obj="网络解散";
                myhandle.sendMessage(massge);
                break;
            case 14:
                try {
                    Device device = DeviceManage.getInstance().getDevice(deviceid);
                    device.setOnline(true);
                    DeviceManage.getInstance().addDevice(device);
                    App.db.update(SubDevice.class, WhereBuilder.b("id", "=", nId).and("gateway_id", "=", deviceid), new KeyValue("online", true));
                }
                catch (DbException e) {
                    e.printStackTrace();
                }
                EventBus.getDefault().post(new EventData(EventData.CODE_GETDEVICE,"OK"));
                break;
            case 16:
                deleData(deviceid);
                EventBus.getDefault().post(new EventData(EventData.TAG_REFRESH,""));
                break;
        }
    }

    private void deleData(int deviceid) {
//        Device gateway=DeviceManage.getInstance().getDevice(deviceid);
//        DeviceManage.getInstance().removeDevice(gateway.getMacAddress());
//        DeviceManage.getInstance().removeCurrentdev(gateway);
        try {
            List<SubDevice> subs = App.db.selector(SubDevice.class).where("gateway_id","=",deviceid).findAll();
            List<Sensor> sensors=App.db.selector(Sensor.class).where("device_id","=",deviceid).findAll();
            List<Panel> panels=App.db.selector(Panel.class).where("gateway_id","=",deviceid).findAll();
            List<Scenebean> scenebeen=App.db.selector(Scenebean.class).where("gateway_id","=",deviceid).findAll();
            if(subs!=null){
                App.db.delete(subs);
                for (int i = 0; i < subs.size(); i++) {
                    String objectId = subs.get(i).getObjectId();
                    HttpManage.getInstance().deleSub(objectId,HttpManage.SUBTABLE, new Callback.CommonCallback<String>() {
                        @Override
                        public void onSuccess(String result) {

                        }

                        @Override
                        public void onError(Throwable ex, boolean isOnCallback) {

                        }

                        @Override
                        public void onCancelled(CancelledException cex) {

                        }

                        @Override
                        public void onFinished() {

                        }
                    });
                }

            }
            if(sensors!=null){
                App.db.delete(sensors);
                for (int i = 0; i < sensors.size(); i++) {
                    String objectId =sensors.get(i).getObjectId();
                    HttpManage.getInstance().deleSub(objectId,HttpManage.SENSORTABLE, new Callback.CommonCallback<String>() {
                        @Override
                        public void onSuccess(String result) {

                        }

                        @Override
                        public void onError(Throwable ex, boolean isOnCallback) {

                        }

                        @Override
                        public void onCancelled(CancelledException cex) {

                        }

                        @Override
                        public void onFinished() {

                        }
                    });
                }
            }
            if(panels!=null){
                for (int i = 0; i < panels.size(); i++) {
                    PanelManage.getInstance().removePanel(panels.get(i).getMac());
                    String objectId =panels.get(i).getObjectId();
                    HttpManage.getInstance().deleSub(objectId,"panel", new Callback.CommonCallback<String>() {
                        @Override
                        public void onSuccess(String result) {

                        }

                        @Override
                        public void onError(Throwable ex, boolean isOnCallback) {

                        }

                        @Override
                        public void onCancelled(CancelledException cex) {

                        }

                        @Override
                        public void onFinished() {

                        }
                    });
                }
            }
            if(scenebeen!=null){
                App.db.delete(scenebeen);
                for (int i = 0; i < scenebeen.size(); i++) {
                    Scenebean entity = scenebeen.get(i);
                    HttpManage.getInstance().deleSub(entity.getObjectId(), HttpManage.SCENETABLE, new MyCallback() {
                        @Override
                        public void onSuc(String result) {

                        }

                        @Override
                        public void onFail(int code, String msg) {

                        }
                    });
                }
            }
        } catch (DbException e) {
            e.printStackTrace();
        }
    }
}
