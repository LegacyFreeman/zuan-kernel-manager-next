package com.zuan.kernelmanager.next.core.kernel

import android.content.Context

/** Isolated hardware smoke validator kept out of the full diagnostics compiler method. */
object KernelHardwareControlValidator {
    suspend fun run(context: Context, snapshot: KernelCapabilityEngine.Snapshot): KernelEngineFullDiagnostics.HardwareControlReport {
        val attempted=mutableListOf<String>(); val passed=mutableListOf<String>(); val skipped=mutableListOf<String>(); val failures=mutableListOf<String>()
        suspend fun check(name:String, enabled:Boolean, block:suspend()->Boolean){ if(!enabled){skipped+=name;return};attempted+=name;runCatching{block()}.onSuccess{if(it)passed+=name else failures+=name}.onFailure{failures+="$name:${it.message?:it.javaClass.simpleName}"} }
        KernelRuntimeContext.context=context.applicationContext; KernelRootRuntime.initialize(context.applicationContext)
        val cpu=snapshot.topology.cpuPolicies.firstOrNull(); val base=cpu?.let{"/sys/devices/system/cpu/cpufreq/$it"}
        val gov=base?.let{"$it/scaling_governor"}; val min=base?.let{"$it/scaling_min_freq"}; val max=base?.let{"$it/scaling_max_freq"}; val cpus=listOfNotNull(gov,min,max); val cw=KernelRootRuntime.nodeWritableBatch(cpus)
        val g=gov?.let{KernelRootRuntime.readNode(it)}.orEmpty(); val mn=min?.let{KernelRootRuntime.readNode(it).toLongOrNull()}; val mx=max?.let{KernelRootRuntime.readNode(it).toLongOrNull()}
        check("cpu-governor",snapshot.has("cpu_freq")&&g.isNotBlank()&&gov!=null&&cw[gov]==true){
            val policy = cpu ?: return@check false
            val governorPath = gov ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.CpuGovernor(policy,g)).success
        }
        check("cpu-range",snapshot.has("cpu_freq")&&mn!=null&&mx!=null&&mn>0&&mx>=mn&&min!=null&&max!=null&&cw[min]==true&&cw[max]==true){
            val policy = cpu ?: return@check false
            val minKHz = mn ?: return@check false
            val maxKHz = mx ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.CpuRange(policy,minKHz,maxKHz)).success
        }
        val u=listOf("/proc/sys/kernel/sched_util_clamp_min","/proc/sys/kernel/sched_util_clamp_max"); val ue=KernelRootRuntime.nodeExistsBatch(u); val uw=KernelRootRuntime.nodeWritableBatch(u.filter{ue[it]==true}); val umin=KernelRootRuntime.readNode(u[0]).toIntOrNull(); val umax=KernelRootRuntime.readNode(u[1]).toIntOrNull()
        check("uclamp",snapshot.has("uclamp")&&umin!=null&&umax!=null&&uw[u[0]]==true&&uw[u[1]]==true){
            val minValue = umin ?: return@check false
            val maxValue = umax ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.Uclamp(minValue,maxValue)).success
        }
        val dev=snapshot.topology.gpuDevfreq.firstOrNull(); val gb=dev?.let{"/sys/class/devfreq/$it"}; val gg=gb?.let{"$it/governor"}; val gm=gb?.let{"$it/min_freq"}; val gx=gb?.let{"$it/max_freq"}; val gw=KernelRootRuntime.nodeWritableBatch(listOfNotNull(gg,gm,gx)); val gv=gg?.let{KernelRootRuntime.readNode(it)}.orEmpty(); val gmin=gm?.let{KernelRootRuntime.readNode(it).toLongOrNull()}; val gmax=gx?.let{KernelRootRuntime.readNode(it).toLongOrNull()}
        check("gpu-governor",dev!=null&&gg!=null&&gv.isNotBlank()&&gw[gg]==true&&snapshot.has("gpu_governor")){
            val gpuName = dev ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.GpuGovernor(gpuName,gv)).success
        }
        check("gpu-range",dev!=null&&gmin!=null&&gmax!=null&&gmin>0&&gmax>=gmin&&gm!=null&&gx!=null&&gw[gm]==true&&gw[gx]==true&&snapshot.has("gpu_min_max")){
            val gpuName = dev ?: return@check false
            val minHz = gmin ?: return@check false
            val maxHz = gmax ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.GpuRange(gpuName,minHz,maxHz)).success
        }
        val safe=listOf("/proc/sys/vm/swappiness","/proc/sys/vm/vfs_cache_pressure","/proc/sys/net/ipv4/tcp_keepalive_time","/proc/sys/net/ipv4/tcp_timestamps"); val se=KernelRootRuntime.nodeExistsBatch(safe); val sw=KernelRootRuntime.nodeWritableBatch(safe.filter{se[it]==true}); val pair=safe.firstNotNullOfOrNull{p->if(se[p]==true&&sw[p]==true){val v=KernelRootRuntime.readNode(p);if(v.isNotBlank())p to v else null}else null}
        check("safe-node-write",pair!=null){
            val safePair = pair ?: return@check false
            KernelBackendEngine.apply(context,KernelBackendEngine.ControlRequest.NodeWrite(safePair.first,safePair.second,true)).success
        }
        val status=when{failures.isNotEmpty()->KernelEngineFullDiagnostics.Status.FAIL;attempted.isEmpty()->KernelEngineFullDiagnostics.Status.SKIP;else->KernelEngineFullDiagnostics.Status.PASS}
        return KernelEngineFullDiagnostics.HardwareControlReport(status,attempted.size,passed.size,skipped.size,failures.size,"tested=${attempted.size} pass=${passed.size} skip=${skipped.size} fail=${failures.size}"+(if(failures.isEmpty())"" else " • "+failures.joinToString(",")))
    }
}
