# Java async HttpClient Benachmark

测试Java异步Http客户端性能，包含如下两种方式：

1. JMH：项目本身已经按照JMH标准进行处理，打包编译生成`benchmark.jar`后可直接执行各项JMH测试，注意异步代码并不直接适用于普通的JMH方法，这里进行了一些特殊处理，详见代码。

   `java -jar benchmark.jar -h`

2. 人工代码执行：由于异步客户端的特性，部分数据无法直接通过JMH测试获得，因此增加了人工编写循环代码进行测试，包括吞吐量、最小延迟、最大延迟、平均延迟、错误数量等指标。

   `java -cp benchmark.jar com.tony.ManualTest -h`

## 库版本

* [async-http-client](https://github.com/AsyncHttpClient/async-http-client)：3.0.0.Beta1
* [Apache Http Client](https://github.com/apache/httpcomponents-client)：5.2.1
* JDK Http Client：OpenJDK 11.0.11 Corretto
* [Jetty Client](https://github.com/eclipse/jetty.project)：11.0.13
* [OkHttp](https://github.com/square/okhttp)：4.10.0 （Ok Http并非异步http客户端，放在这里做比较）

## 测试数据

**注意测试方式并不十分科学，如下测试结果并不精准，且受限于测试环境，仅能反映部分情况**。

客户端：MacOS intel i5 4c8g

服务端：Linux  amd64 1c2g

```
// 人工代码测试
ahc Analyse:    time=9840ms  ops=3048.78  minLatency=0ms  maxLatency=1267ms avgLatency=230.918ms totalCnt=30000 success=28901 fail=1099
apache Analyse: time=11818ms ops=2538.5   minLatency=13ms maxLatency=336ms  avgLatency=37.115ms  totalCnt=30000 success=30000 fail=0
jdk Analyse:    time=11364ms ops=2639.915 minLatency=14ms maxLatency=754ms  avgLatency=77.865ms  totalCnt=30000 success=30000 fail=0
jetty Analyse:  time=17859ms ops=1679.825 minLatency=14ms maxLatency=285ms  avgLatency=27.078ms  totalCnt=30000 success=30000 fail=0
ok Analyse:     time=12327ms ops=2433.682 minLatency=17ms maxLatency=1059ms avgLatency=444.362ms totalCnt=30000 success=30000 fail=0


// JMH 测试
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Ahc.throughput:success               2       400        N/A   <url>  thrpt   5  2009.386 ±  64.015  ops
JMH_Async_Apache.throughput:success            4       400        N/A  <url>  thrpt    5  1211.992 ± 3109.381 ops
JMH_Async_Jdk.throughput:success             N/A       400        200  <url>  thrpt    5   457.336 ± 1193.343 ops
JMH_Async_Jetty.throughput:success             2       400        500  <url>  thrpt    5  1128.708 ± 120.255  ops
JMH_Async_Ok.throughput:success              N/A       400        200  <url>  thrpt    5  2083.072 ± 2278.559 ops

JMH_Async_Ahc.throughput:fail                  2       400        N/A   <url>  thrpt   5       ≈ 0            ops
JMH_Async_Apache.throughput:fail               4       400        N/A  <url>  thrpt    5   303.466 ±  937.938 ops
JMH_Async_Jdk.throughput:fail                N/A       400        200  <url>  thrpt    5  1695.590 ± 1379.885 ops
JMH_Async_Jetty.throughput:fail                2       400        500  <url>  thrpt    5     0.440 ±   3.788  ops
JMH_Async_Ok.throughput:fail                 N/A       400        200  <url>  thrpt    5       ≈ 0            ops
```

### Appendix

#### Ahc

```
代码循环测试结果：
配置：ioThread=4 maxConn=1000
time=11693ms ops=2565.637 minLatency=12ms maxLatency=611ms avgLatency=40.972ms totalCnt=30000 success=30000 fail=0
time=11930ms ops=2514.668 minLatency=12ms maxLatency=1798ms avgLatency=114.633ms totalCnt=30000 success=30000 fail=0

配置：ioThread=2 maxConn=1000
time=11945ms ops=2511.511 minLatency=13ms maxLatency=1170ms avgLatency=62.679ms totalCnt=30000 success=30000 fail=0
time=11896ms ops=2521.856 minLatency=13ms maxLatency=1035ms avgLatency=58.411ms totalCnt=30000 success=30000 fail=0

配置：ioThread=4 maxConn=10000
time=10537ms ops=2847.11 minLatency=0ms maxLatency=1561ms avgLatency=225.274ms totalCnt=30000 success=28837 fail=1163
time=9840ms ops=3048.78 minLatency=0ms maxLatency=1267ms avgLatency=230.918ms totalCnt=30000 success=28901 fail=1099

配置：ioThread=2 maxConn=10000
time=10604ms ops=2829.121 minLatency=0ms maxLatency=1558ms avgLatency=245.267ms totalCnt=30000 success=28243 fail=1757
time=10147ms ops=2956.538 minLatency=0ms maxLatency=1313ms avgLatency=240.453ms totalCnt=30000 success=28789 fail=1211


JMH测试结果：
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Ahc.throughput:fail                  4       800        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               4       800        N/A  <url>  thrpt    5  1009.795 ±  12.540  ops

JMH_Async_Ahc.throughput:fail                  2       500        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               2       500        N/A  <url>  thrpt    5  1601.166 ±   82.679 ops
JMH_Async_Ahc.throughput:fail                  4       500        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               4       500        N/A  <url>  thrpt    5  1576.797 ±  313.427 ops

JMH_Async_Ahc.throughput:fail                  2       400        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               2       400        N/A  <url>  thrpt    5  2009.386 ±  64.015  ops
JMH_Async_Ahc.throughput:fail                  4       400        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               4       400        N/A  <url>  thrpt    5  1986.070 ±  93.884  ops

JMH_Async_Ahc.throughput:fail                  2       300        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ahc.throughput:success               2       300        N/A  <url>  thrpt    5  2345.277 ± 2150.474 ops
JMH_Async_Ahc.throughput:fail                  4       300        N/A  <url>  thrpt    5   765.297 ± 2012.967 ops
JMH_Async_Ahc.throughput:success               4       300        N/A  <url>  thrpt    5  1777.535 ± 3118.518 ops
```

#### Apache

```
代码循环测试结果：
配置：ioThread=4 maxConn=2000
apache Analyse: time=15965ms ops=1879.11 minLatency=16ms maxLatency=7258ms avgLatency=1213.898ms totalCnt=30000 success=29851 fail=149
apache Analyse: time=16454ms ops=1823.264 minLatency=14ms maxLatency=5101ms avgLatency=249.995ms totalCnt=30000 success=29967 fail=33

配置：ioThread=4 maxConn=5000
apache Analyse: time=11966ms ops=2507.103 minLatency=14ms maxLatency=1125ms avgLatency=79.361ms totalCnt=30000 success=30000 fail=0
apache Analyse: time=33415ms ops=897.8 minLatency=0ms maxLatency=25461ms avgLatency=5670.409ms totalCnt=30000 success=24821 fail=5179

配置：ioThread=2 maxConn=5000
apache Analyse: time=11818ms ops=2538.5 minLatency=13ms maxLatency=336ms avgLatency=37.115ms totalCnt=30000 success=30000 fail=0
apache Analyse: time=60130ms ops=498.919 minLatency=14ms maxLatency=56337ms avgLatency=12013.781ms totalCnt=30000 success=24218 fail=5782


JMH测试结果：
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Apache.throughput:fail               2       800        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Apache.throughput:success            2       800        N/A  <url>  thrpt    5   989.798 ±  45.287  ops

JMH_Async_Apache.throughput:fail               2       500        N/A  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Apache.throughput:success            2       500        N/A  <url>  thrpt    5  1562.352 ±   13.666 ops
JMH_Async_Apache.throughput:fail               4       500        N/A  <url>  thrpt    5     0.360 ±    3.099 ops
JMH_Async_Apache.throughput:success            4       500        N/A  <url>  thrpt    5  1519.874 ±  299.341 ops

JMH_Async_Apache.throughput:fail               2       400        N/A  <url>  thrpt    5   264.396 ±  855.973 ops
JMH_Async_Apache.throughput:success            2       400        N/A  <url>  thrpt    5    48.268 ±  206.461 ops
JMH_Async_Apache.throughput:fail               4       400        N/A  <url>  thrpt    5   303.466 ±  937.938 ops
JMH_Async_Apache.throughput:success            4       400        N/A  <url>  thrpt    5  1211.992 ± 3109.381 ops

JMH_Async_Apache.throughput:fail               2       300        N/A  <url>  thrpt    5   181.563 ±  839.487 ops
JMH_Async_Apache.throughput:success            2       300        N/A  <url>  thrpt    5   726.542 ± 2838.224 ops
JMH_Async_Apache.throughput:fail               4       300        N/A  <url>  thrpt    5   368.323 ±  462.292 ops
JMH_Async_Apache.throughput:success            4       300        N/A  <url>  thrpt    5   252.713 ±  882.288 ops
```

#### Jdk

```
配置：threads=80
jdk Analyse: time=24535ms ops=1222.743 minLatency=0ms maxLatency=15884ms avgLatency=1860.178ms totalCnt=30000 success=25074 fail=4926
jdk Analyse: time=21197ms ops=1415.294 minLatency=0ms maxLatency=18924ms avgLatency=1509.164ms totalCnt=30000 success=10107 fail=19893

配置：threads=160
jdk Analyse: time=11364ms ops=2639.915 minLatency=14ms maxLatency=754ms avgLatency=77.865ms totalCnt=30000 success=30000 fail=0
jdk Analyse: time=12765ms ops=2350.176 minLatency=15ms maxLatency=3011ms avgLatency=153.846ms totalCnt=30000 success=30000 fail=0


JMH测试结果：
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Jdk.throughput:fail                N/A       800        200  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Jdk.throughput:success             N/A       800        200  <url>  thrpt    5  1018.363 ±  12.915  ops

JMH_Async_Jdk.throughput:fail                N/A       500        200  <url>  thrpt    5   811.087 ± 2910.912 ops
JMH_Async_Jdk.throughput:success             N/A       500        200  <url>  thrpt    5   984.601 ± 2989.315 ops
JMH_Async_Jdk.throughput:fail                N/A       500        500  <url>  thrpt    5   848.602 ± 1394.002 ops
JMH_Async_Jdk.throughput:success             N/A       500        500  <url>  thrpt    5   862.133 ± 1551.958 ops

JMH_Async_Jdk.throughput:fail                N/A       400        200  <url>  thrpt    5  1695.590 ± 1379.885 ops/
JMH_Async_Jdk.throughput:success             N/A       400        200  <url>  thrpt    5   457.336 ± 1193.343 ops/
JMH_Async_Jdk.throughput:fail                N/A       400        500  <url>  thrpt    5  1709.322 ± 1179.833 ops/
JMH_Async_Jdk.throughput:success             N/A       400        500  <url>  thrpt    5   369.377 ±  805.489 ops/

JMH_Async_Jdk.throughput:fail                N/A       300        200  <url>  thrpt    5  2322.076 ± 1771.556 ops
17
JMH_Async_Jdk.throughput:success             N/A       300        200  <url>  thrpt    5   443.679 ± 1279.748 ops
18
JMH_Async_Jdk.throughput:fail                N/A       300        500  <url>  thrpt    5  2374.938 ± 2318.097 ops
19
JMH_Async_Jdk.throughput:success             N/A       300        500  <url>  thrpt    5   323.184 ±  737.558 ops
```

#### Jetty

```
代码循环测试结果：
配置：ioThread=4 threads=80
jetty Analyse: time=17859ms ops=1679.825 minLatency=14ms maxLatency=285ms avgLatency=27.078ms totalCnt=30000 success=30000 fail=0
jetty Analyse: time=19884ms ops=1508.75 minLatency=13ms maxLatency=349ms avgLatency=38.54ms totalCnt=30000 success=30000 fail=0

配置：ioThread=4 threads=160
jetty Analyse: time=19119ms ops=1569.119 minLatency=13ms maxLatency=1294ms avgLatency=102.222ms totalCnt=30000 success=30000 fail=0
jetty Analyse: time=18617ms ops=1611.43 minLatency=13ms maxLatency=269ms avgLatency=29.236ms totalCnt=30000 success=30000 fail=0

配置：ioThread=2 threads=80
jetty Analyse: time=20949ms ops=1432.049 minLatency=13ms maxLatency=1636ms avgLatency=124.954ms totalCnt=30000 success=30000 fail=0
jetty Analyse: time=21249ms ops=1411.831 minLatency=13ms maxLatency=2070ms avgLatency=41.546ms totalCnt=30000 success=30000 fail=0
比较奇怪这个数据，之前有一次测出来很高，但是后面一直是1500这样，不清楚是哪里出了问题。


JMH测试结果：
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Jetty.throughput:fail                2       800        200  <url>  thrpt    5     0.400 ±   3.443  ops
JMH_Async_Jetty.throughput:success             2       800        200  <url>  thrpt    5   736.230 ±  49.283  ops
JMH_Async_Jetty.throughput:fail                2       800        500  <url>  thrpt    5     0.280 ±   2.409  ops
JMH_Async_Jetty.throughput:success             2       800        500  <url>  thrpt    5   765.956 ±   8.593  ops

JMH_Async_Jetty.throughput:fail                2        500        200  <url>  thrpt    5     0.560 ±   3.002  ops
JMH_Async_Jetty.throughput:success             2        500        200  <url>  thrpt    5   994.607 ± 113.364  ops
JMH_Async_Jetty.throughput:fail                2        500        500  <url>  thrpt    5     0.200 ±   1.721  ops
JMH_Async_Jetty.throughput:success             2        500        500  <url>  thrpt    5  1004.147 ±  43.758  ops

JMH_Async_Jetty.throughput:fail                2       400        200  <url>  thrpt    5     0.960 ±   8.264  ops
JMH_Async_Jetty.throughput:success             2       400        200  <url>  thrpt    5  1116.852 ± 155.364  ops
JMH_Async_Jetty.throughput:fail                2       400        500  <url>  thrpt    5     0.440 ±   3.788  ops
JMH_Async_Jetty.throughput:success             2       400        500  <url>  thrpt    5  1128.708 ± 120.255  ops

JMH_Async_Jetty.throughput:fail                2       300        200  <url>  thrpt    5     6.835 ±  53.401  ops
JMH_Async_Jetty.throughput:success             2       300        200  <url>  thrpt    5  1313.552 ± 167.241  ops
JMH_Async_Jetty.throughput:fail                2       300        500  <url>  thrpt    5     0.440 ±   3.786  ops
JMH_Async_Jetty.throughput:success             2       300        500  <url>  thrpt    5  1296.548 ±  45.161  ops
```

#### Ok http

```
代码循环测试结果：
配置：thread=80
ok Analyse: time=12327ms ops=2433.682 minLatency=17ms maxLatency=1059ms avgLatency=444.362ms totalCnt=30000 success=30000 fail=0
ok Analyse: time=12680ms ops=2365.93 minLatency=15ms maxLatency=1429ms avgLatency=723.459ms totalCnt=30000 success=30000 fail=0

配置：thread=160
ok Analyse: time=14175ms ops=2116.402 minLatency=14ms maxLatency=2612ms avgLatency=738.446ms totalCnt=30000 success=30000 fail=0
ok Analyse: time=12563ms ops=2387.964 minLatency=15ms maxLatency=1616ms avgLatency=442.212ms totalCnt=30000 success=30000 fail=0


JMH测试结果：
Benchmark                            (ioThreads)  (parkUs)  (threads)  (url)   Mode  Cnt    Score     Error  Units
JMH_Async_Ok.throughput:fail                 N/A       800        200  <url>  thrpt    5       ≈ 0            ops
JMH_Async_Ok.throughput:success              N/A       800        200  <url>  thrpt    5  1020.969 ±  15.254  ops

JMH_Async_Ok.throughput:fail                 N/A       500        200  <url>  thrpt    5     2.800 ±    5.633 ops
JMH_Async_Ok.throughput:success              N/A       500        200  <url>  thrpt    5  1642.612 ±   18.185 ops

JMH_Async_Ok.throughput:fail                 N/A       400        100  <url>  thrpt    5     2.039 ±    2.138 ops/
JMH_Async_Ok.throughput:success              N/A       400        100  <url>  thrpt    5  2018.264 ± 1871.679 ops/
JMH_Async_Ok.throughput:fail                 N/A       400        200  <url>  thrpt    5       ≈ 0            ops/
JMH_Async_Ok.throughput:success              N/A       400        200  <url>  thrpt    5  2083.072 ± 2278.559 ops/

JMH_Async_Ok.throughput:fail                 N/A       300        100  <url>  thrpt    5     1.720 ±    4.576 ops
JMH_Async_Ok.throughput:success              N/A       300        100  <url>  thrpt    5  2580.253 ± 1590.747 ops
JMH_Async_Ok.throughput:fail                 N/A       300        200  <url>  thrpt    5     0.200 ±    1.722 ops
JMH_Async_Ok.throughput:success              N/A       300        200  <url>  thrpt    5  2016.103 ± 1068.391 ops
```

