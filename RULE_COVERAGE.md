# Rule coverage and validation matrix

## Normative baseline

Normative sources, read on 2026-08-08:

1. WMO Mahjong Competition Rules "Green Book," hosted by EMA:
   https://mahjong-europe.org/portal/images/docs/mcr_EN.pdf
2. EMA MCR tournament regulations:
   https://mahjong-europe.org/portal/images/docs/mcr_regulations.pdf

The Java implementation ports scoring branches from GB-Mahjong revision
`a2911ad6447f31aa17e553350f9ec5dffb7d3053` into typed Java, but the old native
code is only a differential oracle. A mismatch is adjudicated against the sources
above; native output is never auto-blessed.

Status vocabulary:

- `REGRESSION`: covered by an executable migrated or newly authored regression.
- `DIFFERENTIAL`: compared with an MIT-licensed upstream fixture, subject to a
  normative-source ruling when results differ.
- `IMPLEMENTED`: a reachable detection and exclusion path exists; broader
  independent golden validation remains.
- `INTERNAL`: typed representation for a documented scoring case that is not a
  separate member of the official 81-name fan list.

## Official 81-fan matrix

| Points | Fans | Status |
|---:|---|---|
| 88 | DASIXI, DASANYUAN, LVYISE, JIULIANBAODENG, SIGANG, LIANQIDUI, SHISANYAO | Implemented; differential/regression coverage across the tier |
| 64 | QINGYAOJIU, XIAOSIXI, XIAOSANYUAN, ZIYISE, SIANKE, YISESHUANGLONGHUI | Implemented; differential/regression coverage across the tier |
| 48 | YISESITONGSHUN, YISESIJIEGAO | Implemented; both under differential regression |
| 32 | YISESIBUGAO, SANGANG, HUNYAOJIU | Implemented; differential/regression coverage across the tier |
| 24 | QIDUI, QIXINGBUKAO, QUANSHUANGKE, QINGYISE, YISESANTONGSHUN, YISESANJIEGAO, QUANDA, QUANZHONG, QUANXIAO | Implemented; differential/regression coverage across the tier |
| 16 | QINGLONG, SANSESHUANGLONGHUI, YISESANBUGAO, QUANDAIWU, SANTONGKE, SANANKE | Implemented; differential/regression coverage across the tier |
| 12 | QUANBUKAO, ZUHELONG, DAYUWU, XIAOYUWU, SANFENGKE | Implemented; differential/regression coverage across the tier |
| 8 | HUALONG, TUIBUDAO, SANSESANTONGSHUN, SANSESANJIEGAO, WUFANHU, MIAOSHOUHUICHUN, HAIDILAOYUE, GANGSHANGKAIHUA, QIANGGANGHU | Implemented; differential/regression coverage across the tier |
| 6 | PENGPENGHU, HUNYISE, SANSESANBUGAO, WUMENQI, QUANQIUREN, SHUANGANGANG, SHUANGJIANKE | Implemented; differential/regression coverage across the tier |
| 4 | QUANDAIYAO, BUQIUREN, SHUANGMINGGANG, HUJUEZHANG | Implemented; differential/regression coverage across the tier |
| 2 | JIANKE, QUANFENGKE, MENFENGKE, MENQIANQING, PINGHU, SIGUIYI, SHUANGTONGKE, SHUANGANKE, ANGANG, DUANYAO | Implemented; differential/regression coverage across the tier |
| 1 | YIBANGAO, XIXIANGFENG, LIANLIU, LAOSHAOFU, YAOJIUKE, MINGGANG, QUEYIMEN, WUZI, BIANZHANG, KANZHANG, DANDIAOJIANG, ZIMO, HUAPAI | Implemented; differential/regression coverage across the tier |

`FanCatalogTest` asserts that the public enum contains exactly these 81 entries.
There are no placeholder entries and no generic "unknown fan" success path.

## Normative and local rulings under regression

| Ruling | Expected behavior |
|---|---|
| Mixed concealed + melded kong | Six points, as explicitly stated under Green Book fan 57; represented as `InternalCombination`, not an extra official `Fan` |
| Formal multi-wait | An exhausted second formal wait prevents false DANDIAOJIANG/BIANZHANG/KANZHANG |
| Eight-point minimum | Uses `qualifyingFan`; flowers cannot satisfy it |
| Self-draw wait | A seven-point discard result may be a legal eight-point self-draw wait |
| Settlement | Discard: discarder pays fan+8, others 8; self draw: every opponent pays fan+8; deltas sum to zero |

The formal multi-wait interpretation is intentionally availability-independent for
fan classification. The public shape/wait APIs still reject a physically impossible
fifth copy. This distinction remains a release-certification item because the Green
Book defines these fans as "waiting solely" without specifying an exhausted-copy
algorithm.

## Differential corpus and adjudications

`LegacyCppDifferentialRegressionTest` migrates 55 representative scoring hands and
19 formal/physical wait fixtures from upstream `unit_test.cpp`. Together they cover
all point tiers, high-fan exclusions, non-identical/non-reuse combination selection,
the kong matrix, special shapes, win modes, and exhausted-tile waits. The upstream
MIT notice is retained in `NOTICE`.

Known adjudications:

| Fixture or branch | Native oracle | Java ruling |
|---|---|---|
| One concealed + one melded kong | Private `MINGANGANG`, incorrectly worth 5 | 6 points, following the Green Book fan-57 text |
| `123456m456p11789s` | Accepts either Mixed Double Chow or Short Straight | Deterministically selects Short Straight; both are 1 point and produce the same maximum |
| After-kong flag without a kong | Historical caller state could be semantically incomplete | Rejected before evaluation |
| Robbing-kong with owned copies or last-wall-tile flag | Historical validation was partial | Rejected as physically impossible |

## Remaining validation work

No scoring branch is intentionally unimplemented, so the evaluator does not return
`UNSUPPORTED` for a known MCR fan. Nevertheless, release 1.0 should remain blocked
until an independently authored corpus provides, for each of the 81 fans:

1. a positive hand;
2. a one-tile near miss;
3. required exclusion/non-duplication cases;
4. concealed/exposed and self-draw/discard variants where relevant;
5. a source edition and section identifier.

Imported upstream fixtures must retain the upstream MIT notice. The differential
corpus protects migration behavior; it does not replace independent certification.
