// =============================================================================
//  Pancreas HFRT SIB  |  PTV 4.0 / 2.5 Gy x 15 Fx  |  Plan Check
//  Varian Eclipse 18.1 ESAPI  -  Single-file plug-in (읽기 전용)
// -----------------------------------------------------------------------------
//  파일명 : Pancreas_HFRT_SIB_4.0_2.5Gy_15Fx_Check.cs
//           Windows 파일명에는 '/'를 쓸 수 없어 "4.0_2.5"로 표기했습니다.
//           (실행 창 제목과 결과 복사 텍스트에는 "4.0/2.5"로 표시)
//
//  처방   : PTV_4.0  4.0 Gy x 15 Fx = 60   Gy  (SIB)
//           PTV_2.5  2.5 Gy x 15 Fx = 37.5 Gy
//
//  평가 기준
//   [1] Target
//       PTV_2.5 : V37.5Gy > 98 %
//       GTV     : V60Gy   > 95 %   (90 ~ 95 % = 허용(Minor), 90 % 이하 = FAIL)
//   [2] Normal organ
//       1) iStomach, iDuodenum 각각 : Dmax < 41 Gy
//       2) Liver  : Dmean < 24 Gy,  24 Gy 미만을 받는 부피 >= 700 cc
//       3) Cord   : Dmax < 30 Gy
//       4) Kidney : V15Gy < 45 %,  V20Gy < 33 %   (좌/우 각각 + 양측 합산)
//   [참고] PTV_4.0 V60Gy, Plan 3D Dmax  (판정 없이 값만 표시)
//
//  사용법 : 플랜(또는 Plan Sum)을 연 상태에서 Tools > Scripts > 이 파일 > Run
//           구조물 이름이 다르면 창 위쪽 "구조물 매핑" 콤보박스에서 고르면 즉시 재평가
//
//  주의   : - C# 5 문법만 사용 (Eclipse single-file plug-in 컴파일러 호환:
//             $"..." 문자열 보간, ?. 연산자, => 멤버 등 C# 6+ 문법 사용 금지)
//           - 파일 인코딩은 UTF-8 (BOM 포함) 유지 -> 한글 깨짐 방지
//           - 기준 / 구조물 후보 이름 수정은 아래 Protocol 클래스에서
// =============================================================================

using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using VMS.TPS.Common.Model.API;
using VMS.TPS.Common.Model.Types;

[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]
[assembly: AssemblyInformationalVersion("1.0")]

// 읽기 전용 스크립트 (환자/플랜 데이터를 수정하지 않음) -> 아래 줄은 주석 그대로 두세요.
// [assembly: ESAPIScript(IsWriteable = true)]

namespace VMS.TPS
{
    // =========================================================================
    //  Entry point
    // =========================================================================
    public class Script
    {
        public Script()
        {
        }

        [MethodImpl(MethodImplOptions.NoInlining)]
        public void Execute(ScriptContext context, System.Windows.Window window)
        {
            window.Title = Protocol.WindowTitle;
            window.FontFamily = new FontFamily("Malgun Gothic, Segoe UI");
            window.FontSize = 12.5;
            window.Background = Ui.Page;
            window.SizeToContent = SizeToContent.Manual;
            window.Width = Math.Max(1000, Math.Min(1320, SystemParameters.WorkArea.Width - 40));
            window.Height = Math.Max(600, Math.Min(900, SystemParameters.WorkArea.Height - 40));
            window.MinWidth = 1000;
            window.MinHeight = 520;
            window.WindowStartupLocation = WindowStartupLocation.CenterScreen;

            try
            {
                PlanCheckView view = new PlanCheckView(context, window);
                window.Content = view.Build();
            }
            catch (Exception ex)
            {
                window.Content = Ui.MessagePanel("스크립트 실행 중 오류가 발생했습니다.", ex.ToString());
            }
        }
    }

    // =========================================================================
    //  [설정] 프로토콜 - 기준을 바꾸려면 이 클래스만 수정하면 됩니다.
    // =========================================================================
    internal static class Protocol
    {
        public const string Version = "1.0";
        public const string WindowTitle = "Pancreas HFRT SIB 4.0/2.5 Gy x 15 Fx - Plan Check";
        public const string DisplayTitle = "Pancreas HFRT SIB   PTV 4.0 / 2.5 Gy × 15 Fx";

        public const int Fractions = 15;
        public const double HighDosePerFx = 4.0;                    // Gy/Fx (PTV_4.0, GTV)
        public const double LowDosePerFx = 2.5;                     // Gy/Fx (PTV_2.5)
        public const double HighTotal = HighDosePerFx * Fractions;  // 60   Gy
        public const double LowTotal = LowDosePerFx * Fractions;    // 37.5 Gy

        // Dmax 정의
        //   false : 점 최대선량 (DVH Max = Eclipse DVH 통계의 Max Dose)  <- 기본값
        //   true  : D0.03cc
        public static readonly bool DmaxAsD003cc = false;

        // 신장 평가 방식 (둘 다 true 면 둘 다 표시)
        //   KidneyEach     : 좌/우 신장 각각 평가
        //   KidneyCombined : 양측 합산 (좌 Vx cc + 우 Vx cc) / (좌 + 우 부피)
        public static readonly bool KidneyEach = true;
        public static readonly bool KidneyCombined = true;

        // ---- 역할(Role) 키 ----
        public const string GTV = "GTV";
        public const string PTV_HIGH = "PTV_4.0";
        public const string PTV_LOW = "PTV_2.5";
        public const string STOMACH = "iStomach";
        public const string DUODENUM = "iDuodenum";
        public const string LIVER = "Liver";
        public const string CORD = "Cord";
        public const string KIDNEY_L = "Kidney_L";
        public const string KIDNEY_R = "Kidney_R";

        // ---- 결과표 구분 ----
        public const string G_RX = "Rx";
        public const string G_TARGET = "[1] Target";
        public const string G_OAR = "[2] OAR";
        public const string G_INFO = "참고";

        // ---- 구조물 자동 매칭 후보 ----
        //  앞쪽 이름이 우선. 비교 시 대소문자, 공백, '_', '-', 괄호는 무시 ('.'은 구분).
        //  예) "PTV_4.0" = "PTV 4.0" = "ptv-4.0",   "Kidney_L" = "Kidney L" = "KidneyL"
        //  ProtocolId(3번째 인자)를 지정하면, 다른 구조물로 평가할 때 비고에 경고 표시.
        public static List<RoleDef> Roles()
        {
            List<RoleDef> r = new List<RoleDef>();
            r.Add(new RoleDef(GTV, "GTV", null,
                "GTV", "iGTV", "GTV_60", "GTV_60Gy", "GTV_6000", "GTVp", "GTV_Pancreas"));
            r.Add(new RoleDef(PTV_HIGH, "PTV_4.0 (60Gy)", null,
                "PTV_4.0", "PTV_4.0Gy", "PTV_60", "PTV_60Gy", "PTV_6000", "PTV_High", "PTV_Boost", "PTV_SIB"));
            r.Add(new RoleDef(PTV_LOW, "PTV_2.5 (37.5Gy)", null,
                "PTV_2.5", "PTV_2.5Gy", "PTV_37.5", "PTV_37.5Gy", "PTV_3750", "PTV_Low"));
            r.Add(new RoleDef(STOMACH, "iStomach", "iStomach",
                "iStomach", "Stomach_i", "Stomach"));
            r.Add(new RoleDef(DUODENUM, "iDuodenum", "iDuodenum",
                "iDuodenum", "Duodenum_i", "Duodenum"));
            r.Add(new RoleDef(LIVER, "Liver", null,
                "Liver"));
            r.Add(new RoleDef(CORD, "Spinal Cord", null,
                "SpinalCord", "Cord", "SC", "Cord_Spinal"));
            r.Add(new RoleDef(KIDNEY_L, "Kidney_L", null,
                "Kidney_L", "Kidney_Lt", "Kidney_Left", "L_Kidney", "Lt_Kidney", "Left_Kidney"));
            r.Add(new RoleDef(KIDNEY_R, "Kidney_R", null,
                "Kidney_R", "Kidney_Rt", "Kidney_Right", "R_Kidney", "Rt_Kidney", "Right_Kidney"));
            return r;
        }

        // ---- 평가 기준 ----
        //  new Constraint(구분, 역할, 항목, 파라미터, 비교, 기준)
        //    VolumeAtDosePct : 파라미터 = 선량(Gy)  -> Vx Gy (%)
        //    SparedVolumeCc  : 파라미터 = 선량(Gy)  -> x Gy 미만 받는 부피 (cc)
        //    DoseAtVolumePct : 파라미터 = 부피(%)   -> Dx % (Gy)
        //    MaxDose / MeanDose : 파라미터 사용 안 함 (0)
        //  { Acceptable = x } : 기준 미달이지만 x 는 만족하면 "허용(Minor)"
        public static List<Constraint> Constraints()
        {
            List<Constraint> c = new List<Constraint>();

            // [1] Target
            c.Add(new Constraint(G_TARGET, PTV_LOW, MetricKind.VolumeAtDosePct, LowTotal, Cmp.GT, 98.0));
            c.Add(new Constraint(G_TARGET, GTV, MetricKind.VolumeAtDosePct, HighTotal, Cmp.GT, 95.0) { Acceptable = 90.0 });

            // [2] Normal organ
            c.Add(new Constraint(G_OAR, STOMACH, MetricKind.MaxDose, 0, Cmp.LT, 41.0));
            c.Add(new Constraint(G_OAR, DUODENUM, MetricKind.MaxDose, 0, Cmp.LT, 41.0));
            c.Add(new Constraint(G_OAR, LIVER, MetricKind.MeanDose, 0, Cmp.LT, 24.0));
            c.Add(new Constraint(G_OAR, LIVER, MetricKind.SparedVolumeCc, 24.0, Cmp.GE, 700.0));
            c.Add(new Constraint(G_OAR, CORD, MetricKind.MaxDose, 0, Cmp.LT, 30.0));
            if (KidneyEach)
            {
                c.Add(new Constraint(G_OAR, KIDNEY_L, MetricKind.VolumeAtDosePct, 15.0, Cmp.LT, 45.0));
                c.Add(new Constraint(G_OAR, KIDNEY_L, MetricKind.VolumeAtDosePct, 20.0, Cmp.LT, 33.0));
                c.Add(new Constraint(G_OAR, KIDNEY_R, MetricKind.VolumeAtDosePct, 15.0, Cmp.LT, 45.0));
                c.Add(new Constraint(G_OAR, KIDNEY_R, MetricKind.VolumeAtDosePct, 20.0, Cmp.LT, 33.0));
            }
            if (KidneyCombined)
            {
                c.Add(new Constraint(G_OAR, new[] { KIDNEY_L, KIDNEY_R }, MetricKind.VolumeAtDosePct, 15.0, Cmp.LT, 45.0));
                c.Add(new Constraint(G_OAR, new[] { KIDNEY_L, KIDNEY_R }, MetricKind.VolumeAtDosePct, 20.0, Cmp.LT, 33.0));
            }

            // 참고 (판정 없이 값만 표시)
            c.Add(new Constraint(G_INFO, PTV_HIGH, MetricKind.VolumeAtDosePct, HighTotal, Cmp.GT, double.NaN) { InfoOnly = true });
            return c;
        }
    }

    // =========================================================================
    //  Model
    // =========================================================================
    internal enum MetricKind { MaxDose, MeanDose, VolumeAtDosePct, SparedVolumeCc, DoseAtVolumePct }
    internal enum Cmp { LT, LE, GT, GE }
    internal enum Verdict { Pass, Acceptable, Fail, Check, Info }

    internal class RoleDef
    {
        public string Key;
        public string Label;
        public string ProtocolId;
        public string[] Aliases;

        public RoleDef(string key, string label, string protocolId, params string[] aliases)
        {
            Key = key;
            Label = label;
            ProtocolId = protocolId;
            Aliases = aliases;
        }
    }

    internal class Constraint
    {
        public string Group;
        public string[] Roles;            // 2개 이상이면 합산 평가 (예: 좌+우 신장)
        public MetricKind Kind;
        public double Param;
        public Cmp Op;
        public double Goal;
        public double Acceptable = double.NaN;
        public bool InfoOnly;

        public Constraint(string group, string role, MetricKind kind, double param, Cmp op, double goal)
            : this(group, new[] { role }, kind, param, op, goal)
        {
        }

        public Constraint(string group, string[] roles, MetricKind kind, double param, Cmp op, double goal)
        {
            Group = group;
            Roles = roles;
            Kind = kind;
            Param = param;
            Op = op;
            Goal = goal;
        }

        public bool IsCoverage
        {
            get { return Op == Cmp.GT || Op == Cmp.GE; }
        }

        public string Unit()
        {
            switch (Kind)
            {
                case MetricKind.VolumeAtDosePct: return "%";
                case MetricKind.SparedVolumeCc: return "cc";
                default: return "Gy";
            }
        }

        public string ItemLabel()
        {
            switch (Kind)
            {
                case MetricKind.MaxDose: return Protocol.DmaxAsD003cc ? "Dmax (D0.03cc)" : "Dmax";
                case MetricKind.MeanDose: return "Dmean";
                case MetricKind.VolumeAtDosePct: return "V" + Fmt.Num(Param) + "Gy";
                case MetricKind.SparedVolumeCc: return "Vol < " + Fmt.Num(Param) + "Gy";
                case MetricKind.DoseAtVolumePct: return "D" + Fmt.Num(Param) + "%";
                default: return "";
            }
        }

        public string GoalLabel()
        {
            if (InfoOnly) return "(참고)";
            string s = Fmt.OpSymbol(Op) + " " + Fmt.Num(Goal) + " " + Unit();
            if (!double.IsNaN(Acceptable))
                s += "   (허용 " + Fmt.OpSymbol(Op) + " " + Fmt.Num(Acceptable) + " " + Unit() + ")";
            return s;
        }
    }

    internal class CheckRow
    {
        public string Group = "";
        public string StructureId = "";
        public string Item = "";
        public string Goal = "";
        public string Value = "-";
        public Verdict Status = Verdict.Check;
        public string Note = "";
    }

    internal class PlanChoice
    {
        public PlanningItem Item;
        public bool IsSum;
        public string CourseId;
        public string Display;

        public PlanChoice(PlanningItem item)
        {
            Item = item;
            IsSum = item is PlanSum;
            CourseId = PlanUtil.CourseId(item);
            Display = (IsSum ? "[Plan Sum]  " : "[Plan]  ") + CourseId + " / " + item.Id;
        }

        public bool SameAs(PlanningItem other)
        {
            if (other == null) return false;
            return IsSum == (other is PlanSum) && Item.Id == other.Id && CourseId == PlanUtil.CourseId(other);
        }

        public override string ToString()
        {
            return Display;
        }
    }

    internal class StructChoice
    {
        public Structure S;
        public string Display;

        public StructChoice(Structure s)
        {
            S = s;
            if (s == null) Display = "(선택 안 함)";
            else Display = s.IsEmpty ? s.Id + "   (empty)" : s.Id;
        }

        public override string ToString()
        {
            return Display;
        }
    }

    // =========================================================================
    //  구조물 자동 매칭
    // =========================================================================
    internal static class StructureMatcher
    {
        public static string Normalize(string id)
        {
            if (id == null) return "";
            StringBuilder sb = new StringBuilder();
            foreach (char ch in id.ToLowerInvariant())
            {
                if (char.IsLetterOrDigit(ch) || ch == '.') sb.Append(ch);
            }
            return sb.ToString();
        }

        public static bool SameId(string a, string b)
        {
            return Normalize(a) == Normalize(b);
        }

        // 1순위: contour 가 있는 구조물,  2순위: 빈 구조물 (후보 이름 순서대로)
        public static Structure Match(IList<Structure> structures, RoleDef role)
        {
            for (int pass = 0; pass < 2; pass++)
            {
                foreach (string alias in role.Aliases)
                {
                    string key = Normalize(alias);
                    foreach (Structure s in structures)
                    {
                        if (Normalize(s.Id) != key) continue;
                        if (pass == 0 && s.IsEmpty) continue;
                        return s;
                    }
                }
            }
            return null;
        }

        public static bool IsPoint(Structure s)
        {
            string t = s.DicomType ?? "";
            return t.Equals("MARKER", StringComparison.OrdinalIgnoreCase)
                || t.Equals("ISOCENTER", StringComparison.OrdinalIgnoreCase);
        }
    }

    // =========================================================================
    //  Plan / dose 유틸
    // =========================================================================
    internal static class PlanUtil
    {
        public static bool IsAbs(DoseValue.DoseUnit u)
        {
            return u == DoseValue.DoseUnit.Gy || u == DoseValue.DoseUnit.cGy;
        }

        public static double ToGy(DoseValue d)
        {
            if (d.Unit == DoseValue.DoseUnit.Gy) return d.Dose;
            if (d.Unit == DoseValue.DoseUnit.cGy) return d.Dose / 100.0;
            return double.NaN;
        }

        public static double TryGy(Func<DoseValue> getter)
        {
            try { return ToGy(getter()); }
            catch { return double.NaN; }
        }

        public static StructureSet GetStructureSet(PlanningItem p)
        {
            PlanSetup ps = p as PlanSetup;
            if (ps != null) return ps.StructureSet;
            PlanSum sum = p as PlanSum;
            if (sum != null) return sum.StructureSet;
            return null;
        }

        public static Dose GetDose(PlanningItem p)
        {
            PlanSetup ps = p as PlanSetup;
            if (ps != null) return ps.Dose;
            PlanSum sum = p as PlanSum;
            if (sum != null) return sum.Dose;
            return null;
        }

        public static string CourseId(PlanningItem p)
        {
            try
            {
                Course c = null;
                PlanSetup ps = p as PlanSetup;
                if (ps != null) c = ps.Course;
                PlanSum sum = p as PlanSum;
                if (sum != null) c = sum.Course;
                return c == null ? "" : c.Id;
            }
            catch
            {
                return "";
            }
        }

        public static int Fx(PlanSetup ps)
        {
            return ps.NumberOfFractions.HasValue ? ps.NumberOfFractions.Value : 0;
        }

        public static bool DoseStatus(PlanningItem p, out string problem)
        {
            problem = null;
            PlanSetup ps = p as PlanSetup;
            if (ps != null)
            {
                if (ps.Dose == null) { problem = "선량이 계산되지 않았습니다."; return false; }
                if (!ps.IsDoseValid) { problem = "선량이 유효하지 않습니다 (재계산 필요)."; return false; }
                return true;
            }
            PlanSum sum = p as PlanSum;
            if (sum != null)
            {
                if (sum.Dose == null) { problem = "Plan Sum 선량이 없습니다."; return false; }
                return true;
            }
            problem = "지원하지 않는 플랜 형식입니다.";
            return false;
        }

        // 시스템 선량 단위 (Gy / cGy). 모르면 Unknown -> 첫 DVH 결과로 확정
        public static DoseValue.DoseUnit DetectUnit(PlanningItem p)
        {
            try
            {
                PlanSetup ps = p as PlanSetup;
                if (ps != null && IsAbs(ps.TotalDose.Unit)) return ps.TotalDose.Unit;
                PlanSum sum = p as PlanSum;
                if (sum != null)
                {
                    foreach (PlanSetup c in sum.PlanSetups)
                        if (IsAbs(c.TotalDose.Unit)) return c.TotalDose.Unit;
                }
                Dose d = GetDose(p);
                if (d != null && IsAbs(d.DoseMax3D.Unit)) return d.DoseMax3D.Unit;
            }
            catch
            {
            }
            return DoseValue.DoseUnit.Unknown;
        }

        // 3D 선량 최대값 (Gy). 상대선량 표시 상태여도 Gy 로 환산
        public static double MaxDose3DGy(PlanningItem p)
        {
            try
            {
                Dose d = GetDose(p);
                if (d == null) return double.NaN;
                DoseValue m = d.DoseMax3D;
                double gy = ToGy(m);
                if (!double.IsNaN(gy)) return gy;
                PlanSetup ps = p as PlanSetup;
                if (ps != null && m.Unit == DoseValue.DoseUnit.Percent)
                {
                    double rx = ToGy(ps.TotalDose);
                    if (!double.IsNaN(rx)) return m.Dose / 100.0 * rx;
                }
            }
            catch
            {
            }
            return double.NaN;
        }

        public static string Describe(PlanningItem p)
        {
            try
            {
                PlanSetup ps = p as PlanSetup;
                if (ps != null)
                {
                    return string.Format("Rx  {0} Gy × {1} Fx = {2} Gy     |     {3}",
                        Fmt.F2(TryGy(delegate { return ps.DosePerFraction; })), Fx(ps),
                        Fmt.F2(TryGy(delegate { return ps.TotalDose; })), ps.ApprovalStatus);
                }
                PlanSum sum = p as PlanSum;
                if (sum != null)
                {
                    List<string> parts = new List<string>();
                    foreach (PlanSetup c in sum.PlanSetups)
                        parts.Add(string.Format("{0} ({1} Gy × {2})", c.Id, Fmt.F2(TryGy(delegate { return c.DosePerFraction; })), Fx(c)));
                    return "Plan Sum  =  " + string.Join("  +  ", parts.ToArray());
                }
            }
            catch
            {
            }
            return "";
        }
    }

    // =========================================================================
    //  평가 엔진
    // =========================================================================
    internal class Evaluator
    {
        private readonly PlanningItem _plan;
        private readonly bool _doseOk;
        private readonly string _doseProblem;
        private DoseValue.DoseUnit _unit;
        private readonly Dictionary<string, DVHData> _dvh = new Dictionary<string, DVHData>();

        public Evaluator(PlanningItem plan)
        {
            _plan = plan;
            _doseOk = PlanUtil.DoseStatus(plan, out _doseProblem);
            _unit = PlanUtil.DetectUnit(plan);
        }

        // ---------------- 처방 / 분할 ----------------
        public void AddPlanRows(List<CheckRow> rows)
        {
            PlanSetup ps = _plan as PlanSetup;
            PlanSum sum = _plan as PlanSum;
            if (ps != null)
            {
                int fx = PlanUtil.Fx(ps);
                double dpf = PlanUtil.TryGy(delegate { return ps.DosePerFraction; });
                double total = PlanUtil.TryGy(delegate { return ps.TotalDose; });
                bool fxOk = fx == Protocol.Fractions;
                bool high = Near(dpf, Protocol.HighDosePerFx);
                bool low = Near(dpf, Protocol.LowDosePerFx);

                CheckRow r = new CheckRow();
                r.Group = Protocol.G_RX;
                r.StructureId = ps.Id;
                r.Item = "처방 / 분할";
                r.Goal = string.Format("{0} Fx,  {1} 또는 {2} Gy/Fx", Protocol.Fractions,
                    Fmt.F1(Protocol.HighDosePerFx), Fmt.F1(Protocol.LowDosePerFx));
                r.Value = string.Format("{0} Gy × {1} Fx = {2} Gy", Fmt.F2(dpf), fx, Fmt.F2(total));
                r.Status = (fxOk && (high || low)) ? Verdict.Pass : Verdict.Check;
                List<string> notes = new List<string>();
                if (!fxOk) notes.Add("※ 분할 횟수가 프로토콜(" + Protocol.Fractions + " Fx)과 다름");
                if (high) notes.Add("고선량 레벨(PTV_4.0, " + Fmt.Num(Protocol.HighTotal) + " Gy) 기준 처방");
                else if (low) notes.Add("저선량 레벨(PTV_2.5, " + Fmt.Num(Protocol.LowTotal) + " Gy) 기준 처방");
                else notes.Add("※ 선량/분할이 " + Fmt.F1(Protocol.HighDosePerFx) + " 또는 "
                    + Fmt.F1(Protocol.LowDosePerFx) + " Gy 가 아님 - 확인");
                r.Note = string.Join("   ", notes.ToArray());
                rows.Add(r);
            }
            else if (sum != null)
            {
                int fxSum = 0;
                List<string> parts = new List<string>();
                foreach (PlanSetup p in sum.PlanSetups)
                {
                    int fx = PlanUtil.Fx(p);
                    fxSum += fx;
                    parts.Add(string.Format("{0}: {1} Gy × {2}", p.Id, Fmt.F2(PlanUtil.TryGy(delegate { return p.DosePerFraction; })), fx));
                }
                CheckRow r = new CheckRow();
                r.Group = Protocol.G_RX;
                r.StructureId = sum.Id;
                r.Item = "분할 합계";
                r.Goal = "Σ " + Protocol.Fractions + " Fx";
                r.Value = "Σ " + fxSum + " Fx";
                r.Status = fxSum == Protocol.Fractions ? Verdict.Pass : Verdict.Check;
                r.Note = string.Join("  +  ", parts.ToArray()) + "   (Plan Sum 구성/가중치는 직접 확인)";
                rows.Add(r);
            }

            if (!_doseOk)
            {
                CheckRow d = new CheckRow();
                d.Group = Protocol.G_RX;
                d.StructureId = _plan.Id;
                d.Item = "선량";
                d.Goal = "계산 완료";
                d.Status = Verdict.Check;
                d.Note = _doseProblem;
                rows.Add(d);
            }
        }

        // ---------------- 참고: 3D Dmax ----------------
        public void AddPlanMaxRow(List<CheckRow> rows)
        {
            CheckRow r = new CheckRow();
            r.Group = Protocol.G_INFO;
            r.StructureId = "Plan (3D)";
            r.Item = "Dmax";
            r.Goal = "(참고)";
            r.Status = Verdict.Info;
            double gy = _doseOk ? PlanUtil.MaxDose3DGy(_plan) : double.NaN;
            if (!double.IsNaN(gy))
            {
                r.Value = Fmt.F2(gy) + " Gy";
                r.Note = Fmt.F1(gy / Protocol.HighTotal * 100.0) + " % (" + Fmt.Num(Protocol.HighTotal) + " Gy 기준)";
            }
            rows.Add(r);
        }

        // ---------------- 기준 1개 평가 ----------------
        public CheckRow Evaluate(Constraint c, Dictionary<string, Structure> map, List<RoleDef> roles)
        {
            CheckRow row = new CheckRow();
            row.Group = c.Group;
            row.Item = c.ItemLabel();
            row.Goal = c.GoalLabel();

            List<Structure> ss = new List<Structure>();
            List<string> ids = new List<string>();
            List<string> missing = new List<string>();
            foreach (string key in c.Roles)
            {
                Structure s;
                if (map.TryGetValue(key, out s) && s != null)
                {
                    ss.Add(s);
                    ids.Add(s.Id);
                }
                else
                {
                    missing.Add(RoleLabel(roles, key));
                    ids.Add(RoleLabel(roles, key) + " ?");
                }
            }
            row.StructureId = string.Join(" + ", ids.ToArray());
            if (c.Roles.Length > 1) row.StructureId += "  (합산)";

            List<string> notes = new List<string>();
            if (missing.Count > 0)
            {
                row.Status = Verdict.Check;
                notes.Add("구조물 미지정: " + string.Join(", ", missing.ToArray()) + "  -> 위 '구조물 매핑'에서 선택");
            }
            else if (ss.Any(s => s.IsEmpty))
            {
                row.Status = Verdict.Check;
                notes.Add("빈 구조물 (contour 없음): " + string.Join(", ", ss.Where(s => s.IsEmpty).Select(s => s.Id).ToArray()));
            }
            else if (!_doseOk)
            {
                row.Status = Verdict.Check;
                notes.Add(_doseProblem);
            }
            else
            {
                try
                {
                    double value = Compute(c, ss, notes);
                    string unit = c.Unit();
                    double rounded = Fmt.Round(value, unit);   // 표시 자릿수로 반올림한 값으로 판정
                    row.Value = Fmt.Value(rounded, unit);
                    if (double.IsNaN(value)) row.Status = Verdict.Check;
                    else if (c.InfoOnly) row.Status = Verdict.Info;
                    else row.Status = Judge(c, rounded);
                }
                catch (Exception ex)
                {
                    row.Status = Verdict.Check;
                    row.Value = "-";
                    notes.Add("계산 오류: " + ex.Message);
                }
                notes.InsertRange(0, Warnings(c, map, roles, ss));
            }

            if (c.InfoOnly && row.Status == Verdict.Check) row.Status = Verdict.Info;
            row.Note = string.Join("   ", notes.ToArray());
            return row;
        }

        private List<string> Warnings(Constraint c, Dictionary<string, Structure> map, List<RoleDef> roles, List<Structure> ss)
        {
            List<string> w = new List<string>();
            foreach (string key in c.Roles)
            {
                RoleDef rd = roles.FirstOrDefault(x => x.Key == key);
                Structure s;
                if (rd == null || rd.ProtocolId == null || !map.TryGetValue(key, out s) || s == null) continue;
                if (!StructureMatcher.SameId(rd.ProtocolId, s.Id))
                    w.Add("※ 프로토콜 구조물 '" + rd.ProtocolId + "' 대신 '" + s.Id + "'(으)로 평가");
            }
            foreach (Structure s in ss)
            {
                DVHData d;
                if (!_dvh.TryGetValue(s.Id, out d) || d == null) continue;
                double cov = d.Coverage;
                if (cov > 1.0001) cov /= 100.0;   // 0..1 이 아닌 % 로 오는 경우 대비
                if (cov < 0.99)
                    w.Add("※ " + s.Id + " 일부가 선량계산영역 밖 (Dose cover " + Fmt.F1(cov * 100.0) + " %)");
            }
            return w;
        }

        private double Compute(Constraint c, List<Structure> ss, List<string> notes)
        {
            switch (c.Kind)
            {
                case MetricKind.MaxDose: return MaxDose(ss, notes);
                case MetricKind.MeanDose: return MeanDose(ss, notes);
                case MetricKind.VolumeAtDosePct: return VolumeAtDosePct(c, ss, notes);
                case MetricKind.SparedVolumeCc: return SparedVolumeCc(c, ss, notes);
                case MetricKind.DoseAtVolumePct: return DoseAtVolumePct(c, ss, notes);
                default: return double.NaN;
            }
        }

        // Dmax : 점 최대선량 (또는 D0.03cc). 합산 구조물이면 그 중 최대
        private double MaxDose(List<Structure> ss, List<string> notes)
        {
            double result = double.NaN;
            foreach (Structure s in ss)
            {
                Structure st = s;
                double pointMax = PlanUtil.ToGy(RequireDvh(st).MaxDose);
                double d003 = Safe(delegate { return DAtCc(st, 0.03); });
                double v = Protocol.DmaxAsD003cc ? d003 : pointMax;
                if (double.IsNaN(result) || v > result) result = v;
                string prefix = ss.Count > 1 ? st.Id + " " : "";
                notes.Add(prefix + (Protocol.DmaxAsD003cc
                    ? "Point max " + Fmt.F2(pointMax) + " Gy"
                    : "D0.03cc " + Fmt.F2(d003) + " Gy"));
            }
            notes.Add("Vol " + Fmt.F1(ss.Sum(x => x.Volume)) + " cc");
            return result;
        }

        // Dmean : 합산 구조물이면 부피 가중 평균
        private double MeanDose(List<Structure> ss, List<string> notes)
        {
            double wsum = 0, vsum = 0;
            foreach (Structure s in ss)
            {
                DVHData d = RequireDvh(s);
                double mean = PlanUtil.ToGy(d.MeanDose);
                wsum += mean * s.Volume;
                vsum += s.Volume;
                if (ss.Count > 1) notes.Add(s.Id + " " + Fmt.F2(mean) + " Gy");
                else notes.Add("Dmax " + Fmt.F2(PlanUtil.ToGy(d.MaxDose)) + " Gy");
            }
            notes.Add("Vol " + Fmt.F1(vsum) + " cc");
            if (ss.Count == 1) return PlanUtil.ToGy(RequireDvh(ss[0]).MeanDose);
            return vsum > 0 ? wsum / vsum : double.NaN;
        }

        // Vx Gy (%) : 합산 구조물이면 (Σ Vx cc) / (Σ 부피)
        private double VolumeAtDosePct(Constraint c, List<Structure> ss, List<string> notes)
        {
            foreach (Structure s in ss) RequireDvh(s);   // 시스템 선량 단위 확정
            DoseValue dv = FromGy(c.Param);
            string vLabel = "V" + Fmt.Num(c.Param) + "Gy";

            if (ss.Count == 1)
            {
                Structure s = ss[0];
                double pct = _plan.GetVolumeAtDose(s, dv, VolumePresentation.Relative);
                double cc = _plan.GetVolumeAtDose(s, dv, VolumePresentation.AbsoluteCm3);
                notes.Add(vLabel + " " + Fmt.F1(cc) + " cc");
                if (c.IsCoverage)
                {
                    notes.Add("D98% " + Fmt.F2(Safe(delegate { return DAtPct(s, 98.0); })) + " Gy");
                    notes.Add("D95% " + Fmt.F2(Safe(delegate { return DAtPct(s, 95.0); })) + " Gy");
                }
                notes.Add("Dmean " + Fmt.F2(PlanUtil.ToGy(RequireDvh(s).MeanDose)) + " Gy");
                notes.Add("Vol " + Fmt.F1(s.Volume) + " cc");
                return pct;
            }

            double sumCc = 0, sumVol = 0;
            foreach (Structure s in ss)
            {
                double cc = _plan.GetVolumeAtDose(s, dv, VolumePresentation.AbsoluteCm3);
                double pct = _plan.GetVolumeAtDose(s, dv, VolumePresentation.Relative);
                sumCc += cc;
                sumVol += s.Volume;
                notes.Add(s.Id + " " + Fmt.F2(pct) + " %");
            }
            notes.Add(vLabel + " " + Fmt.F1(sumCc) + " cc / Vol " + Fmt.F1(sumVol) + " cc");
            return sumVol > 0 ? sumCc / sumVol * 100.0 : double.NaN;
        }

        // x Gy 미만을 받는 부피 (cc) = 구조물 부피 - Vx Gy (cc)
        private double SparedVolumeCc(Constraint c, List<Structure> ss, List<string> notes)
        {
            foreach (Structure s in ss) RequireDvh(s);
            DoseValue dv = FromGy(c.Param);
            double total = 0, vcc = 0;
            foreach (Structure s in ss)
            {
                total += s.Volume;
                vcc += _plan.GetVolumeAtDose(s, dv, VolumePresentation.AbsoluteCm3);
            }
            notes.Add("Vol " + Fmt.F1(total) + " cc - V" + Fmt.Num(c.Param) + "Gy " + Fmt.F1(vcc) + " cc");
            return total - vcc;
        }

        // Dx % (Gy) : 단일 구조물만
        private double DoseAtVolumePct(Constraint c, List<Structure> ss, List<string> notes)
        {
            if (ss.Count != 1) throw new NotSupportedException("합산 구조물의 Dx 평가는 지원하지 않습니다.");
            Structure s = ss[0];
            RequireDvh(s);
            notes.Add("Vol " + Fmt.F1(s.Volume) + " cc");
            return DAtPct(s, c.Param);
        }

        // ---------------- ESAPI 호출 래퍼 ----------------
        private DVHData RequireDvh(Structure s)
        {
            DVHData d;
            if (!_dvh.TryGetValue(s.Id, out d))
            {
                // 1 cGy 해상도 (시스템 단위를 아직 모르면 0.1)
                double bin = _unit == DoseValue.DoseUnit.cGy ? 1.0 : (_unit == DoseValue.DoseUnit.Gy ? 0.01 : 0.1);
                d = _plan.GetDVHCumulativeData(s, DoseValuePresentation.Absolute, VolumePresentation.Relative, bin);
                if (d != null && !PlanUtil.IsAbs(_unit) && PlanUtil.IsAbs(d.MaxDose.Unit)) _unit = d.MaxDose.Unit;
                _dvh[s.Id] = d;
            }
            if (d == null) throw new InvalidOperationException(s.Id + ": DVH 를 계산할 수 없습니다 (선량/구조물 확인).");
            return d;
        }

        // GetVolumeAtDose 에 넘길 선량은 시스템 단위(Gy 또는 cGy)로 생성
        private DoseValue FromGy(double gy)
        {
            if (_unit == DoseValue.DoseUnit.cGy) return new DoseValue(gy * 100.0, DoseValue.DoseUnit.cGy);
            return new DoseValue(gy, DoseValue.DoseUnit.Gy);
        }

        private double DAtPct(Structure s, double pct)
        {
            return PlanUtil.ToGy(_plan.GetDoseAtVolume(s, pct, VolumePresentation.Relative, DoseValuePresentation.Absolute));
        }

        private double DAtCc(Structure s, double cc)
        {
            return PlanUtil.ToGy(_plan.GetDoseAtVolume(s, cc, VolumePresentation.AbsoluteCm3, DoseValuePresentation.Absolute));
        }

        private static double Safe(Func<double> f)
        {
            try { return f(); }
            catch { return double.NaN; }
        }

        private static bool Near(double a, double b)
        {
            return !double.IsNaN(a) && Math.Abs(a - b) < 0.005;
        }

        private static string RoleLabel(List<RoleDef> roles, string key)
        {
            RoleDef r = roles.FirstOrDefault(x => x.Key == key);
            return r == null ? key : r.Label;
        }

        private static Verdict Judge(Constraint c, double v)
        {
            if (Compare(v, c.Op, c.Goal)) return Verdict.Pass;
            if (!double.IsNaN(c.Acceptable) && Compare(v, c.Op, c.Acceptable)) return Verdict.Acceptable;
            return Verdict.Fail;
        }

        private static bool Compare(double v, Cmp op, double limit)
        {
            switch (op)
            {
                case Cmp.LT: return v < limit;
                case Cmp.LE: return v <= limit;
                case Cmp.GT: return v > limit;
                default: return v >= limit;
            }
        }
    }

    // =========================================================================
    //  표시 형식
    // =========================================================================
    internal static class Fmt
    {
        private static readonly CultureInfo Inv = CultureInfo.InvariantCulture;

        public static string F2(double v) { return double.IsNaN(v) ? "-" : v.ToString("0.00", Inv); }
        public static string F1(double v) { return double.IsNaN(v) ? "-" : v.ToString("0.0", Inv); }
        public static string Num(double v) { return double.IsNaN(v) ? "-" : v.ToString("0.##", Inv); }

        public static string OpSymbol(Cmp op)
        {
            switch (op)
            {
                case Cmp.LT: return "<";
                case Cmp.LE: return "≤";
                case Cmp.GT: return ">";
                default: return "≥";
            }
        }

        // Gy, % : 소수 2자리 / cc : 소수 1자리
        public static double Round(double v, string unit)
        {
            if (double.IsNaN(v)) return v;
            return Math.Round(v, unit == "cc" ? 1 : 2, MidpointRounding.AwayFromZero);
        }

        public static string Value(double v, string unit)
        {
            if (double.IsNaN(v)) return "-";
            return (unit == "cc" ? F1(v) : F2(v)) + " " + unit;
        }

        public static string VerdictText(Verdict v)
        {
            switch (v)
            {
                case Verdict.Pass: return "PASS";
                case Verdict.Acceptable: return "허용 (Minor)";
                case Verdict.Fail: return "FAIL";
                case Verdict.Check: return "확인 필요";
                default: return "INFO";
            }
        }
    }

    // =========================================================================
    //  UI 공통
    // =========================================================================
    internal static class Ui
    {
        public static SolidColorBrush Rgb(byte r, byte g, byte b)
        {
            SolidColorBrush br = new SolidColorBrush(System.Windows.Media.Color.FromRgb(r, g, b));
            br.Freeze();
            return br;
        }

        public static readonly SolidColorBrush Page = Rgb(0xF4, 0xF6, 0xF9);
        public static readonly SolidColorBrush Navy = Rgb(0x1F, 0x3A, 0x5F);
        public static readonly SolidColorBrush NavySub = Rgb(0xC9, 0xD6, 0xE8);
        public static readonly SolidColorBrush Line = Rgb(0xD5, 0xDB, 0xE3);
        public static readonly SolidColorBrush Ink = Rgb(0x1E, 0x29, 0x3B);
        public static readonly SolidColorBrush Muted = Rgb(0x55, 0x61, 0x72);
        public static readonly SolidColorBrush HeadBg = Rgb(0xE6, 0xEB, 0xF2);
        public static readonly SolidColorBrush Zebra = Rgb(0xF8, 0xFA, 0xFC);
        public static readonly SolidColorBrush FailTint = Rgb(0xFF, 0xF4, 0xF4);

        public static readonly SolidColorBrush PassBg = Rgb(0xD6, 0xF0, 0xDC);
        public static readonly SolidColorBrush PassFg = Rgb(0x14, 0x6C, 0x2E);
        public static readonly SolidColorBrush AccBg = Rgb(0xFF, 0xF0, 0xC0);
        public static readonly SolidColorBrush AccFg = Rgb(0x86, 0x55, 0x00);
        public static readonly SolidColorBrush FailBg = Rgb(0xFB, 0xD5, 0xD5);
        public static readonly SolidColorBrush FailFg = Rgb(0xB0, 0x1E, 0x1E);
        public static readonly SolidColorBrush CheckBg = Rgb(0xFF, 0xE2, 0xC4);
        public static readonly SolidColorBrush CheckFg = Rgb(0xB0, 0x45, 0x00);
        public static readonly SolidColorBrush InfoBg = Rgb(0xE2, 0xED, 0xFA);
        public static readonly SolidColorBrush InfoFg = Rgb(0x1F, 0x4E, 0x8C);

        public static Brush Bg(Verdict v)
        {
            switch (v)
            {
                case Verdict.Pass: return PassBg;
                case Verdict.Acceptable: return AccBg;
                case Verdict.Fail: return FailBg;
                case Verdict.Check: return CheckBg;
                default: return InfoBg;
            }
        }

        public static Brush Fg(Verdict v)
        {
            switch (v)
            {
                case Verdict.Pass: return PassFg;
                case Verdict.Acceptable: return AccFg;
                case Verdict.Fail: return FailFg;
                case Verdict.Check: return CheckFg;
                default: return InfoFg;
            }
        }

        public static TextBlock Text(string s, double size, FontWeight weight, Brush fg)
        {
            return new TextBlock
            {
                Text = s,
                FontSize = size,
                FontWeight = weight,
                Foreground = fg,
                TextWrapping = TextWrapping.Wrap
            };
        }

        public static UIElement MessagePanel(string title, string detail)
        {
            StackPanel sp = new StackPanel { Margin = new Thickness(20) };
            sp.Children.Add(Text(title, 16, FontWeights.Bold, FailFg));
            sp.Children.Add(new TextBox
            {
                Text = detail,
                IsReadOnly = true,
                TextWrapping = TextWrapping.Wrap,
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                FontFamily = new FontFamily("Consolas"),
                Margin = new Thickness(0, 10, 0, 0),
                MaxHeight = 600
            });
            return sp;
        }
    }

    // =========================================================================
    //  메인 화면
    // =========================================================================
    internal class PlanCheckView
    {
        private static readonly double[] ColWidths = { 88, 180, 112, 200, 120, 104, -1 };   // -1 = 나머지
        private static readonly string[] ColHeaders = { "구분", "구조물", "평가 항목", "기준", "결과", "판정", "비고" };

        private readonly ScriptContext _ctx;
        private readonly System.Windows.Window _window;
        private readonly List<RoleDef> _roles = Protocol.Roles();
        private readonly List<Constraint> _constraints = Protocol.Constraints();
        private readonly List<PlanChoice> _plans = new List<PlanChoice>();
        private readonly Dictionary<string, ComboBox> _combos = new Dictionary<string, ComboBox>();
        private readonly Dictionary<string, TextBlock> _comboLabels = new Dictionary<string, TextBlock>();

        private ComboBox _planCombo;
        private TextBlock _rxText;
        private Border _summaryBand;
        private TextBlock _summaryTitle;
        private TextBlock _summaryDetail;
        private Grid _table;
        private TextBlock _status;

        private bool _suspend;
        private List<CheckRow> _rows = new List<CheckRow>();
        private string _summaryLine = "";
        private DateTime _evaluatedAt = DateTime.Now;

        public PlanCheckView(ScriptContext ctx, System.Windows.Window window)
        {
            _ctx = ctx;
            _window = window;
            CollectPlans();
        }

        // 열려 있는 플랜을 기본 선택. Plan Sum 만 열려 있으면 Sum 을 기본 선택
        private void CollectPlans()
        {
            List<PlanningItem> order = new List<PlanningItem>();
            List<PlanSetup> plans = _ctx.PlansInScope == null ? new List<PlanSetup>() : _ctx.PlansInScope.ToList();
            List<PlanSum> sums = _ctx.PlanSumsInScope == null ? new List<PlanSum>() : _ctx.PlanSumsInScope.ToList();
            if (_ctx.PlanSetup != null)
            {
                order.Add(_ctx.PlanSetup);
                order.AddRange(plans.Cast<PlanningItem>());
                order.AddRange(sums.Cast<PlanningItem>());
            }
            else
            {
                order.AddRange(sums.Cast<PlanningItem>());
                order.AddRange(plans.Cast<PlanningItem>());
            }
            foreach (PlanningItem p in order)
            {
                if (p == null || _plans.Any(x => x.SameAs(p))) continue;
                _plans.Add(new PlanChoice(p));
            }
        }

        public UIElement Build()
        {
            Grid root = new Grid();
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            Place(root, BuildHeader(), 0);
            Place(root, BuildSelectors(), 1);
            Place(root, BuildSummary(), 2);
            Place(root, BuildTable(), 3);
            Place(root, BuildFooter(), 4);

            _window.KeyDown += delegate (object sender, KeyEventArgs e)
            {
                if (e.Key == Key.Escape) _window.Close();
            };

            if (_plans.Count == 0)
            {
                ShowNoPlan();
                return root;
            }

            _suspend = true;
            foreach (PlanChoice pc in _plans) _planCombo.Items.Add(pc);
            _planCombo.SelectedIndex = 0;
            _suspend = false;
            OnPlanChanged();
            return root;
        }

        private static void Place(Grid g, UIElement e, int row)
        {
            Grid.SetRow(e, row);
            g.Children.Add(e);
        }

        // ---------------- 화면 구성 ----------------
        private UIElement BuildHeader()
        {
            StackPanel sp = new StackPanel();
            sp.Children.Add(Ui.Text(Protocol.DisplayTitle + "   -   Plan Check", 19, FontWeights.Bold, Brushes.White));
            sp.Children.Add(Ui.Text(string.Format(
                "SIB   PTV_4.0  {0} Gy/Fx = {1} Gy     PTV_2.5  {2} Gy/Fx = {3} Gy     {4} Fx",
                Fmt.F1(Protocol.HighDosePerFx), Fmt.Num(Protocol.HighTotal),
                Fmt.F1(Protocol.LowDosePerFx), Fmt.Num(Protocol.LowTotal), Protocol.Fractions),
                12.5, FontWeights.Normal, Ui.NavySub));

            Patient pt = _ctx.Patient;
            TextBlock ptText = Ui.Text(pt == null ? "환자 정보 없음"
                : string.Format("환자:  {0}, {1}    ({2})", pt.LastName, pt.FirstName, pt.Id),
                13.5, FontWeights.SemiBold, Brushes.White);
            ptText.Margin = new Thickness(0, 6, 0, 0);
            sp.Children.Add(ptText);

            return new Border { Background = Ui.Navy, Padding = new Thickness(18, 12, 18, 12), Child = sp };
        }

        private UIElement BuildSelectors()
        {
            StackPanel sp = new StackPanel();

            StackPanel planRow = new StackPanel { Orientation = System.Windows.Controls.Orientation.Horizontal };
            TextBlock pl = Ui.Text("평가 플랜", 12.5, FontWeights.SemiBold, Ui.Ink);
            pl.Width = 112;
            pl.VerticalAlignment = VerticalAlignment.Center;
            planRow.Children.Add(pl);
            _planCombo = new ComboBox { Width = 420, VerticalContentAlignment = VerticalAlignment.Center };
            _planCombo.SelectionChanged += delegate { if (!_suspend) OnPlanChanged(); };
            planRow.Children.Add(_planCombo);
            _rxText = Ui.Text("", 12.5, FontWeights.Normal, Ui.Muted);
            _rxText.Margin = new Thickness(16, 0, 0, 0);
            _rxText.VerticalAlignment = VerticalAlignment.Center;
            planRow.Children.Add(_rxText);
            sp.Children.Add(planRow);

            TextBlock mt = Ui.Text("구조물 매핑  -  이름으로 자동 선택됨.  다르면 직접 고르면 바로 재평가됩니다.   (주황 라벨 = 미지정 / 프로토콜 이름과 다름)",
                12, FontWeights.Normal, Ui.Muted);
            mt.Margin = new Thickness(0, 10, 0, 2);
            sp.Children.Add(mt);

            WrapPanel wp = new WrapPanel();
            foreach (RoleDef r in _roles)
            {
                StackPanel item = new StackPanel
                {
                    Orientation = System.Windows.Controls.Orientation.Horizontal,
                    Margin = new Thickness(0, 4, 22, 0)
                };
                TextBlock lbl = Ui.Text(r.Label, 12.5, FontWeights.SemiBold, Ui.Ink);
                lbl.Width = 122;
                lbl.VerticalAlignment = VerticalAlignment.Center;
                ComboBox cb = new ComboBox { Width = 168 };
                cb.SelectionChanged += delegate { if (!_suspend) Evaluate(); };
                item.Children.Add(lbl);
                item.Children.Add(cb);
                wp.Children.Add(item);
                _combos[r.Key] = cb;
                _comboLabels[r.Key] = lbl;
            }
            sp.Children.Add(wp);

            return new Border
            {
                Background = Brushes.White,
                BorderBrush = Ui.Line,
                BorderThickness = new Thickness(0, 0, 0, 1),
                Padding = new Thickness(18, 10, 18, 10),
                Child = sp
            };
        }

        private UIElement BuildSummary()
        {
            StackPanel sp = new StackPanel { Orientation = System.Windows.Controls.Orientation.Horizontal };
            _summaryTitle = Ui.Text("-", 20, FontWeights.Bold, Ui.InfoFg);
            _summaryTitle.VerticalAlignment = VerticalAlignment.Center;
            _summaryDetail = Ui.Text("", 13, FontWeights.Normal, Ui.Ink);
            _summaryDetail.Margin = new Thickness(20, 0, 0, 0);
            _summaryDetail.VerticalAlignment = VerticalAlignment.Center;
            sp.Children.Add(_summaryTitle);
            sp.Children.Add(_summaryDetail);

            _summaryBand = new Border
            {
                Background = Ui.InfoBg,
                BorderBrush = Ui.Line,
                BorderThickness = new Thickness(0, 0, 0, 1),
                Padding = new Thickness(18, 10, 18, 10),
                Child = sp
            };
            return _summaryBand;
        }

        private UIElement BuildTable()
        {
            _table = new Grid();
            foreach (double w in ColWidths)
            {
                _table.ColumnDefinitions.Add(new ColumnDefinition
                {
                    Width = w < 0 ? new GridLength(1, GridUnitType.Star) : new GridLength(w)
                });
            }
            _table.ColumnDefinitions[ColWidths.Length - 1].MinWidth = 150;

            Border frame = new Border
            {
                BorderBrush = Ui.Line,
                BorderThickness = new Thickness(1, 1, 0, 0),
                Background = Brushes.White,
                Child = _table
            };

            string dmaxText = Protocol.DmaxAsD003cc ? "Dmax = D0.03cc" : "Dmax = 점 최대선량 (DVH Max)";
            TextBlock legend = Ui.Text(
                "※ 값은 표시 자릿수 (Gy·% 소수 2자리, cc 소수 1자리)로 반올림 후 판정.   " + dmaxText
                + ".   신장 합산 = (좌 Vx cc + 우 Vx cc) / (좌 + 우 부피).   Vx = GetVolumeAtDose (Eclipse DVH).",
                11.5, FontWeights.Normal, Ui.Muted);
            legend.Margin = new Thickness(0, 8, 0, 0);

            StackPanel content = new StackPanel { Margin = new Thickness(18, 12, 18, 12) };
            content.Children.Add(frame);
            content.Children.Add(legend);

            return new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Content = content
            };
        }

        private UIElement BuildFooter()
        {
            DockPanel dp = new DockPanel { LastChildFill = true };

            StackPanel buttons = new StackPanel
            {
                Orientation = System.Windows.Controls.Orientation.Horizontal,
                Margin = new Thickness(0, 8, 18, 8)
            };
            Button copy = new Button { Content = "결과 복사 (클립보드)", Padding = new Thickness(14, 5, 14, 5), Margin = new Thickness(0, 0, 8, 0) };
            copy.Click += delegate { CopyReport(); };
            Button close = new Button { Content = "닫기", Padding = new Thickness(18, 5, 18, 5) };
            close.Click += delegate { _window.Close(); };
            buttons.Children.Add(copy);
            buttons.Children.Add(close);
            DockPanel.SetDock(buttons, Dock.Right);
            dp.Children.Add(buttons);

            _status = Ui.Text("읽기 전용 스크립트 (데이터 수정 없음)   |   v" + Protocol.Version + "   |   Esc: 닫기",
                11.5, FontWeights.Normal, Ui.Muted);
            _status.Margin = new Thickness(18, 0, 0, 0);
            _status.VerticalAlignment = VerticalAlignment.Center;
            dp.Children.Add(_status);

            return new Border
            {
                Background = Brushes.White,
                BorderBrush = Ui.Line,
                BorderThickness = new Thickness(0, 1, 0, 0),
                Child = dp
            };
        }

        // ---------------- 동작 ----------------
        private void OnPlanChanged()
        {
            PlanChoice pc = _planCombo.SelectedItem as PlanChoice;
            if (pc == null) return;
            _rxText.Text = PlanUtil.Describe(pc.Item);
            _suspend = true;
            try
            {
                LoadStructures(pc);
            }
            finally
            {
                _suspend = false;
            }
            Evaluate();
        }

        // 플랜의 구조물 목록으로 콤보박스를 채우고 자동 매칭.
        // 플랜을 바꿔도 같은 ID 구조물이 있으면 직전 선택을 유지.
        private void LoadStructures(PlanChoice pc)
        {
            StructureSet ss = PlanUtil.GetStructureSet(pc.Item);
            List<Structure> list = ss == null
                ? new List<Structure>()
                : ss.Structures.Where(s => !StructureMatcher.IsPoint(s))
                    .OrderBy(s => s.Id, StringComparer.OrdinalIgnoreCase).ToList();

            foreach (RoleDef r in _roles)
            {
                ComboBox cb = _combos[r.Key];
                StructChoice prev = cb.SelectedItem as StructChoice;
                string prevId = (prev != null && prev.S != null) ? prev.S.Id : null;

                List<StructChoice> items = new List<StructChoice>();
                items.Add(new StructChoice(null));
                foreach (Structure s in list) items.Add(new StructChoice(s));
                cb.ItemsSource = items;

                StructChoice pick = null;
                if (prevId != null) pick = items.FirstOrDefault(i => i.S != null && i.S.Id == prevId);
                if (pick == null)
                {
                    Structure auto = StructureMatcher.Match(list, r);
                    if (auto != null) pick = items.FirstOrDefault(i => ReferenceEquals(i.S, auto));
                }
                cb.SelectedItem = pick ?? items[0];
            }
        }

        private Dictionary<string, Structure> CurrentMapping()
        {
            Dictionary<string, Structure> map = new Dictionary<string, Structure>();
            foreach (RoleDef r in _roles)
            {
                StructChoice sc = _combos[r.Key].SelectedItem as StructChoice;
                map[r.Key] = sc == null ? null : sc.S;
            }
            return map;
        }

        private void Evaluate()
        {
            PlanChoice pc = _planCombo.SelectedItem as PlanChoice;
            if (pc == null) return;

            Cursor old = Mouse.OverrideCursor;
            Mouse.OverrideCursor = Cursors.Wait;
            try
            {
                Dictionary<string, Structure> map = CurrentMapping();
                UpdateComboLabels(map);

                Evaluator ev = new Evaluator(pc.Item);
                List<CheckRow> rows = new List<CheckRow>();
                ev.AddPlanRows(rows);
                foreach (Constraint c in _constraints) rows.Add(ev.Evaluate(c, map, _roles));
                ev.AddPlanMaxRow(rows);

                _rows = rows;
                _evaluatedAt = DateTime.Now;
                RenderTable();
                RenderSummary();
                _status.Text = "평가 완료  " + _evaluatedAt.ToString("yyyy-MM-dd HH:mm:ss")
                    + "   |   읽기 전용 (데이터 수정 없음)   |   v" + Protocol.Version + "   |   Esc: 닫기";
            }
            catch (Exception ex)
            {
                MessageBox.Show("평가 중 오류가 발생했습니다.\n\n" + ex, Protocol.WindowTitle);
            }
            finally
            {
                Mouse.OverrideCursor = old;
            }
        }

        private void UpdateComboLabels(Dictionary<string, Structure> map)
        {
            foreach (RoleDef r in _roles)
            {
                TextBlock lbl = _comboLabels[r.Key];
                Structure s = map[r.Key];
                if (s == null)
                {
                    lbl.Foreground = Ui.CheckFg;
                    lbl.ToolTip = "구조물이 선택되지 않았습니다.";
                }
                else if (s.IsEmpty)
                {
                    lbl.Foreground = Ui.CheckFg;
                    lbl.ToolTip = "빈 구조물 (contour 없음)";
                }
                else if (r.ProtocolId != null && !StructureMatcher.SameId(r.ProtocolId, s.Id))
                {
                    lbl.Foreground = Ui.CheckFg;
                    lbl.ToolTip = "프로토콜 지정 구조물(" + r.ProtocolId + ")이 아닌 '" + s.Id + "'로 평가합니다.";
                }
                else
                {
                    lbl.Foreground = Ui.Ink;
                    lbl.ToolTip = null;
                }
            }
        }

        private void RenderTable()
        {
            _table.Children.Clear();
            _table.RowDefinitions.Clear();

            _table.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            for (int c = 0; c < ColHeaders.Length; c++)
                AddCell(0, c, ColHeaders[c], Ui.HeadBg, Ui.Ink, FontWeights.Bold, TextAlignment.Left);

            int r = 1;
            string prevGroup = null;
            foreach (CheckRow row in _rows)
            {
                _table.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
                Brush bg = row.Status == Verdict.Fail ? Ui.FailTint : (r % 2 == 0 ? Ui.Zebra : Brushes.White);
                bool fail = row.Status == Verdict.Fail;

                AddCell(r, 0, row.Group == prevGroup ? "" : row.Group, bg, Ui.Muted, FontWeights.SemiBold, TextAlignment.Left);
                AddCell(r, 1, row.StructureId, bg, Ui.Ink, FontWeights.SemiBold, TextAlignment.Left);
                AddCell(r, 2, row.Item, bg, Ui.Ink, FontWeights.Normal, TextAlignment.Left);
                AddCell(r, 3, row.Goal, bg, Ui.Ink, FontWeights.Normal, TextAlignment.Left);
                AddCell(r, 4, row.Value, bg, fail ? Ui.FailFg : Ui.Ink, FontWeights.Bold, TextAlignment.Right);
                AddCell(r, 5, Fmt.VerdictText(row.Status), Ui.Bg(row.Status), Ui.Fg(row.Status), FontWeights.Bold, TextAlignment.Center);
                AddCell(r, 6, row.Note, bg, Ui.Muted, FontWeights.Normal, TextAlignment.Left);

                prevGroup = row.Group;
                r++;
            }
        }

        private void AddCell(int row, int col, string text, Brush bg, Brush fg, FontWeight weight, TextAlignment align)
        {
            TextBlock tb = new TextBlock
            {
                Text = text ?? "",
                Foreground = fg,
                FontWeight = weight,
                TextAlignment = align,
                TextWrapping = TextWrapping.Wrap,
                VerticalAlignment = VerticalAlignment.Center
            };
            Border b = new Border
            {
                Background = bg,
                BorderBrush = Ui.Line,
                BorderThickness = new Thickness(0, 0, 1, 1),
                Padding = new Thickness(8, 5, 8, 5),
                Child = tb
            };
            Grid.SetRow(b, row);
            Grid.SetColumn(b, col);
            _table.Children.Add(b);
        }

        private void RenderSummary()
        {
            int pass = 0, acc = 0, fail = 0, check = 0, info = 0;
            foreach (CheckRow r in _rows)
            {
                switch (r.Status)
                {
                    case Verdict.Pass: pass++; break;
                    case Verdict.Acceptable: acc++; break;
                    case Verdict.Fail: fail++; break;
                    case Verdict.Check: check++; break;
                    default: info++; break;
                }
            }

            string title;
            Verdict tone;
            if (fail > 0) { title = "FAIL"; tone = Verdict.Fail; }
            else if (check > 0) { title = "확인 필요"; tone = Verdict.Check; }
            else if (acc > 0) { title = "PASS (허용 범위 포함)"; tone = Verdict.Acceptable; }
            else { title = "ALL PASS"; tone = Verdict.Pass; }

            _summaryBand.Background = Ui.Bg(tone);
            _summaryTitle.Text = title;
            _summaryTitle.Foreground = Ui.Fg(tone);
            _summaryDetail.Text = string.Format("PASS {0}     허용 {1}     FAIL {2}     확인 필요 {3}          (참고 {4})",
                pass, acc, fail, check, info);
            _summaryLine = title + "   |   " + _summaryDetail.Text;
        }

        private void ShowNoPlan()
        {
            _summaryBand.Background = Ui.CheckBg;
            _summaryTitle.Text = "플랜 없음";
            _summaryTitle.Foreground = Ui.CheckFg;
            _summaryDetail.Text = "Eclipse 에서 플랜(또는 Plan Sum)을 연 상태로 스크립트를 실행하세요.";
            _planCombo.IsEnabled = false;
            foreach (ComboBox cb in _combos.Values) cb.IsEnabled = false;
        }

        // ---------------- 결과 복사 (탭 구분 -> Excel / Word 표로 붙여넣기) ----------------
        private void CopyReport()
        {
            if (_rows.Count == 0) return;
            try
            {
                Clipboard.SetText(BuildReport());
                _status.Text = "결과를 클립보드에 복사했습니다  -  Excel / Word 에 붙여넣기 하세요.";
            }
            catch (Exception ex)
            {
                MessageBox.Show("클립보드 복사 실패: " + ex.Message, Protocol.WindowTitle);
            }
        }

        private string BuildReport()
        {
            StringBuilder sb = new StringBuilder();
            PlanChoice pc = _planCombo.SelectedItem as PlanChoice;
            Patient pt = _ctx.Patient;

            sb.AppendLine(Protocol.WindowTitle + "   (script v" + Protocol.Version + ")");
            if (pt != null) sb.AppendLine("환자\t" + pt.LastName + ", " + pt.FirstName + "  (" + pt.Id + ")");
            if (pc != null)
            {
                sb.AppendLine("플랜\t" + pc.Display);
                sb.AppendLine("처방\t" + PlanUtil.Describe(pc.Item));
            }
            sb.AppendLine("평가\t" + _evaluatedAt.ToString("yyyy-MM-dd HH:mm") + "   " + UserText());
            sb.AppendLine("결과\t" + _summaryLine);
            sb.AppendLine();
            sb.AppendLine(string.Join("\t", ColHeaders));
            foreach (CheckRow r in _rows)
            {
                sb.AppendLine(string.Join("\t", new[]
                {
                    r.Group, r.StructureId, r.Item, r.Goal, r.Value, Fmt.VerdictText(r.Status), r.Note
                }));
            }
            return sb.ToString();
        }

        private string UserText()
        {
            try
            {
                return _ctx.CurrentUser == null ? "" : "by " + _ctx.CurrentUser.Name + " (" + _ctx.CurrentUser.Id + ")";
            }
            catch
            {
                return "";
            }
        }
    }
}
