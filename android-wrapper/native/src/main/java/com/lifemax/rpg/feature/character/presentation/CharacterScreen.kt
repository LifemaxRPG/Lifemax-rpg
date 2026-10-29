package com.lifemax.rpg.feature.character.presentation

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.character.domain.*
import kotlin.math.*
import com.lifemax.rpg.core.designsystem.LifeMaxTheme

@Composable fun CharacterRoute(vm:CharacterViewModel,onAction:(String,String)->Unit) {
    val data by vm.projection.collectAsState();val ui by vm.selection.collectAsState()
    data?.let { CharacterScreen(it,ui,vm::select,vm::compare,vm::sort,vm::skills,onAction) }
}
@Composable fun CharacterScreen(p:CharacterProjection,ui:CharacterSelection,onSelect:(String)->Unit,onCompare:(Boolean)->Unit,onSort:(String)->Unit,onSkills:()->Unit,onAction:(String,String)->Unit) {
    fun c(k:String)=Color((p.tokens[k]?:0xffeef1f6).toInt())
    val selected=p.attributes.find{it.id==ui.selected}?:p.attributes.maxBy{it.total}
    LifeMaxTheme(p.tokens,p.light) {
        Column(Modifier.fillMaxSize().background(c("bg"))) {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(p.name,Modifier.weight(1f),fontWeight=FontWeight.Bold,maxLines=1)
                TextButton(onClick={onAction("navigate","log")}){Text("＋ Log activity")}
                TextButton(onClick={onAction("navigate","settings")}){Text("⚙")}
            }
            HorizontalDivider(color=c("line"))
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item { CharacterCard {
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(116.dp),contentAlignment=Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()){drawCircle(c("line"),style=Stroke(5.dp.toPx()));drawArc(Brush.linearGradient(listOf(Color(0xff7ab0ff),Color(0xffc58bff))),-90f,360f*(p.pct/100).coerceIn(0f,1f),false,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))}
                            val avatar=remember(p.avatar){runCatching { if(p.avatar.startsWith("data:image/")&&p.avatar.length<2_000_000){val bytes=Base64.decode(p.avatar.substringAfter(','),Base64.DEFAULT);BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()}else null }.getOrNull()}
                            if(avatar!=null)Image(avatar,"Character profile picture",Modifier.size(88.dp).clip(CircleShape)) else Text(p.name.take(1).uppercase(),fontSize=38.sp,fontWeight=FontWeight.Bold)
                            Text("LV ${p.level}",Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(c("accent")).padding(horizontal=13.dp,vertical=3.dp),color=Color(0xff0b0e16),fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.height(18.dp));Text(p.name,fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Text(p.title,fontSize=11.sp,color=c("muted"));Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()){Text(p.xp,Modifier.weight(1f),fontSize=12.sp);Text(p.nextLevel,fontSize=12.sp,color=c("muted"))}
                        CharacterProgress(p.pct,c("accent"));Text(p.nextTitle,fontSize=11.sp,color=c("muted"),modifier=Modifier.padding(top=8.dp))
                        Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.SpaceEvenly){for((label,value)in listOf("STREAK" to p.streak,"TOTAL XP" to p.totalXP,"THIS WEEK" to p.week,"LUCK" to p.luck)){Column(Modifier.clickable(enabled=label=="LUCK"){onAction("luck","")},horizontalAlignment=Alignment.CenterHorizontally){Text(label,fontSize=9.sp,color=c("muted"));Text(value,fontWeight=FontWeight.Bold)}}}
                        HorizontalDivider(Modifier.padding(vertical=14.dp),color=c("line"));Text("WEIGHT  ${p.weight}     ·     BMI  ${p.bmi}",fontSize=12.sp,color=c("muted"))
                    }
                } }
                item { CharacterCard {
                    Text("Build shape",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Row { FilterChip(!ui.compare,{onCompare(false)},{Text("Now")});Spacer(Modifier.width(8.dp));FilterChip(ui.compare,{onCompare(true)},{Text("vs last week")}) }
                    Text(p.archetype,color=c("accent"),fontSize=12.sp)
                    NativeRadar(p.attributes,p.cap,ui.compare,selected.id,onSelect)
                    Text(selected.name,fontWeight=FontWeight.Bold);Text("LV ${selected.level} · ${selected.into.toLong()} / ${selected.needed.toLong()} XP",color=c("muted"),fontSize=12.sp)
                    CharacterProgress(selected.pct,Color(selected.color.toInt()))
                    Text("THIS WEEK  +${selected.week.toLong()} XP   ·   LAST TRAINED  ${selected.last}",fontSize=10.sp,color=c("muted"),modifier=Modifier.padding(top=10.dp))
                    Text(selected.status,color=c("muted"),fontSize=12.sp)
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){val maximum=selected.days.maxOrNull()?.coerceAtLeast(1f)?:1f;selected.days.forEach { value->Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.BottomCenter){Box(Modifier.fillMaxWidth().fillMaxHeight((value/maximum).coerceAtLeast(.08f)).clip(RoundedCornerShape(3.dp)).background(Color(selected.color.toInt()).copy(alpha=if(value>0) .8f else .22f)))}}}
                    TextButton(onClick={onAction("attribute",selected.id)}){Text("Log ${selected.short} activity ›")}
                } }
                item { CharacterCard {
                    Text("Eight core attributes",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items(listOf("level" to "Level","attention" to "Needs attention","week" to "This week")){(mode,label)->FilterChip(ui.sort==mode,{onSort(mode)},{Text(label)})}}
                    val attrs=when(ui.sort){"attention"->p.attributes.sortedWith(compareBy<AttributeProjection>{it.rank}.thenByDescending{it.total});"week"->p.attributes.sortedWith(compareByDescending<AttributeProjection>{it.week}.thenByDescending{it.total});else->p.attributes.sortedByDescending{it.total}}
                    attrs.chunked(2).forEach{pair->Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){pair.forEach{a->Column(Modifier.weight(1f).border(1.dp,c("line"),RoundedCornerShape(12.dp)).clickable{onAction("attribute",a.id)}.padding(12.dp)){Text("LV ${a.level}",color=Color(a.color.toInt()),fontWeight=FontWeight.Bold);Text(a.short+if(a.focus)" ★" else "",fontWeight=FontWeight.Bold);CharacterProgress(a.pct,Color(a.color.toInt()));Text("${a.into.toLong()} / ${a.needed.toLong()}",fontSize=10.sp,color=c("muted"));Text("+${a.week.toLong()} wk",fontSize=10.sp,color=c("good"))}}}}
                    Text("★ marks your class focus. Customize how you earn XP in Settings.",fontSize=11.sp,color=c("muted"),modifier=Modifier.padding(top=12.dp))
                } }
                item { CharacterCard {
                    Text("Skill progression",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    if(p.skills.isEmpty())Text("Skills appear here as you practice them.",color=c("muted"),modifier=Modifier.padding(vertical=20.dp))
                    (if(ui.allSkills)p.skills else p.skills.take(6)).forEach{s->Text(s.name+" · LV "+s.level,modifier=Modifier.padding(top=12.dp),fontWeight=FontWeight.SemiBold);CharacterProgress(s.pct,c("accent"));Text("${s.xp.toLong()} XP",color=c("muted"),fontSize=11.sp)}
                    if(p.skills.size>6)TextButton(onSkills){Text(if(ui.allSkills)"Show fewer" else "Show all ${p.skills.size}")}
                } }
                item { CharacterCard {
                    Text("Real-world benchmarks",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Text("Optional metrics you choose to track.",fontSize=12.sp,color=c("muted"))
                    p.benchmarks.forEach{b->Text(b.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp));Text(b.value,color=c("muted"))}
                    if(p.benchmarks.isEmpty())Text("No benchmarks yet.",color=c("muted"),modifier=Modifier.padding(top=12.dp))
                    TextButton(onClick={onAction("benchmarks","")}){Text("Add or edit in Classic ›")}
                } }
            }
            HorizontalDivider(color=c("line"))
            LazyRow(Modifier.fillMaxWidth().background(c("panel")),contentPadding=PaddingValues(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){items(p.tabs){(id,label)->TextButton(onClick={if(id!="character")onAction("navigate",id)}){Text(label,color=if(id=="character")c("accent") else c("muted"),fontWeight=if(id=="character")FontWeight.Bold else FontWeight.Normal)}}}
        }
    }
}
@Composable private fun CharacterCard(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.3f),RoundedCornerShape(20.dp)).padding(16.dp),content=content)}
@Composable private fun CharacterProgress(pct:Float,color:Color){Box(Modifier.fillMaxWidth().padding(top=8.dp).height(5.dp).clip(CircleShape).background(color.copy(alpha=.12f))){Box(Modifier.fillMaxWidth((pct/100).coerceIn(0f,1f)).fillMaxHeight().background(color))}}
@Composable private fun NativeRadar(attrs:List<AttributeProjection>,cap:Int,compare:Boolean,selected:String,onSelect:(String)->Unit){
    // Presentation geometry only: levels/cap/previous values come from the owner.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=8.dp)){
        val side=maxWidth
        Canvas(Modifier.fillMaxWidth().height(side)){
            val center=Offset(size.width/2,size.height/2);val radius=size.minDimension*.30f
            fun point(i:Int,level:Float):Offset{val angle=-PI/2+i*PI/4;val fraction=.1f+.9f*(level.coerceIn(1f,cap.toFloat())-1)/(cap-1).coerceAtLeast(1);return center+Offset(cos(angle).toFloat()*radius*fraction,sin(angle).toFloat()*radius*fraction)}
            fun polygon(levels:List<Float>)=Path().apply{levels.forEachIndexed{i,l->val v=point(i,l);if(i==0)moveTo(v.x,v.y)else lineTo(v.x,v.y)};close()}
            for(q in listOf(.25f,.5f,.75f,1f))drawPath(polygon(List(8){cap*q}),Color(0xff6b7384).copy(alpha=.3f),style=Stroke(1.dp.toPx()))
            attrs.forEachIndexed{i,_->drawLine(Color(0xff6b7384).copy(alpha=.25f),center,point(i,cap.toFloat()))}
            if(compare)drawPath(polygon(attrs.map{it.previous.toFloat()}),Color(0xffc58bff).copy(alpha=.6f),style=Stroke(1.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8f,5f))))
            val path=polygon(attrs.map{it.level.toFloat()});drawPath(path,Brush.radialGradient(listOf(Color(0x197c9cff),Color(0x759b7bff)),center,radius));drawPath(path,Brush.linearGradient(listOf(Color(0xff7ab0ff),Color(0xffc58bff))),style=Stroke(2.dp.toPx()))
            attrs.forEachIndexed{i,a->drawCircle(Color(a.color.toInt()),if(a.id==selected)6.dp.toPx()else 4.dp.toPx(),point(i,a.level.toFloat()))}
        }
        attrs.forEachIndexed{i,a->val angle=-PI/2+i*PI/4;val x=(.5f+cos(angle).toFloat()*.40f);val y=(.5f+sin(angle).toFloat()*.40f);Column(Modifier.offset(x=side*x-38.dp,y=side*y-18.dp).width(76.dp).clickable{onSelect(a.id)},horizontalAlignment=Alignment.CenterHorizontally){Text(a.short+if(a.focus)" ★" else "",fontSize=10.sp,color=Color(a.color.toInt()),fontWeight=FontWeight.Bold);Text("LV ${a.level}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.65f))}}
    }
}
