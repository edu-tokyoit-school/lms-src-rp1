package jp.co.sss.lms.util;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.mapper.MSectionMapper;

/**
 * 勤怠管理のユーティリティクラス
 * 
 * @author 東京ITスクール
 */
@Component
public class AttendanceUtil {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private MSectionMapper mSectionMapper;

	/**
	 * SSS定時・出退勤時間を元に、遅刻早退を判定をする
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @return 遅刻早退を判定メソッド
	 */
	public AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime) {
		return getStatus(trainingStartTime, trainingEndTime, Constants.SSS_WORK_START_TIME,
				Constants.SSS_WORK_END_TIME);
	}

	/**
	 * 与えられた定時・出退勤時間を元に、遅刻早退を判定する
	 * 
	 * @param trainingStartTime 開始時刻
	 * @param trainingEndTime   終了時刻
	 * @param workStartTime     定時開始時刻
	 * @param workEndTime       定時終了時刻
	 * @return 判定結果
	 */
	private AttendanceStatusEnum getStatus(TrainingTime trainingStartTime,
			TrainingTime trainingEndTime, TrainingTime workStartTime, TrainingTime workEndTime) {
		// 定時が不明な場合、NONEを返却する
		if (workStartTime == null || workStartTime.isBlank() || workEndTime == null
				|| workEndTime.isBlank()) {
			return AttendanceStatusEnum.NONE;
		}
		boolean isLate = false, isEarly = false;
		// 定時より1分以上遅く出社していたら遅刻(＝はセーフ)
		if (trainingStartTime != null && trainingStartTime.isNotBlank()) {
			isLate = (trainingStartTime.compareTo(workStartTime) > 0);
		}
		// 定時より1分以上早く退社していたら早退(＝はセーフ)
		if (trainingEndTime != null && trainingEndTime.isNotBlank()) {
			isEarly = (trainingEndTime.compareTo(workEndTime) < 0);
		}
		if (isLate && isEarly) {
			return AttendanceStatusEnum.TARDY_AND_LEAVING_EARLY;
		}
		if (isLate) {
			return AttendanceStatusEnum.TARDY;
		}
		if (isEarly) {
			return AttendanceStatusEnum.LEAVING_EARLY;
		}
		return AttendanceStatusEnum.NONE;
	}

	/**
	 * 中抜け時間を時(hour)と分(minute)に変換
	 *
	 * @param min 中抜け時間
	 * @return 時(hour)と分(minute)に変換したクラス
	 */
	public TrainingTime calcBlankTime(int min) {
		int hour = min / 60;
		int minute = min % 60;
		TrainingTime total = new TrainingTime(hour, minute);
		return total;
	}

	/**
	 * 時刻分を丸めた本日日付を取得
	 * 
	 * @return "yyyy/M/d"形式の日付
	 */
	public Date getTrainingDate() {
		Date trainingDate;
		try {
			trainingDate = dateUtil.parse(dateUtil.toString(new Date()));
		} catch (ParseException e) {
			// DateUtil#toStringとparseは同様のフォーマットを使用しているため、起こりえないエラー
			throw new IllegalStateException();
		}
		return trainingDate;
	}

	/**
	 * 休憩時間取得
	 * 
	 * @return 休憩時間
	 */
	public LinkedHashMap<Integer, String> setBlankTime() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int i = 15; i < 480;) {
			int hour = i / 60;
			int minute = i % 60;
			String time;

			if (hour == 0) {
				time = minute + "分";

			} else if (minute == 0) {
				time = hour + "時間";
			} else {
				time = hour + "時" + minute + "分";
			}

			map.put(i, time);

			i = i + 15;

		}
		return map;
	}

	/**
	 * 研修日の判定
	 * 
	 * @param courseId
	 * @param trainingDate
	 * @return 判定結果
	 */
	public boolean isWorkDay(Integer courseId, Date trainingDate) {
		Integer count = mSectionMapper.getSectionCountByCourseId(courseId, trainingDate);
		if (count > 0) {
			return true;
		}
		return false;
	}

	//task.26 勤怠時間の編集方法変更
	/**
	 * 時間マップを取得
	 * 
	 * @return 時間マップ
	 */
	public LinkedHashMap<Integer, String> getHourMap() { //キー→実際にフォームで選ばれた時に使う数字。値→画面表示用の"00"等の2桁表示。
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>(); //LinkedHashMapは入れた数字の順番を保つ。→プルダウンは順番に並んでほしい。
		map.put(null, ""); //プルダウンの一番上の空欄の選択肢。
		for (int i = 0; i < 24; i++) {
			map.put(i, String.format("%02d", i)); //二桁に直して選択肢を一つ入れる。
		}
		return map;

	}

	//task.26
	/**
	 * 分マップ(0～59)を取得
	 * 
	 * @return　分マップ
	 */
	public LinkedHashMap<Integer, String> getMinuteMap() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int i = 0; i < 60; i++) {
			map.put(i, String.format("%02d", i));
		}
		return map;
	}
	
	//task.26
	/**
	 * 時刻文字列("HH:mm")から「時間」を抽出する。
	 *
	 * @param timeStr 時刻文字列(例:"09:15")
	 * @return 時間(Integer)。取得できない場合はnull
	 */
	public Integer getHour(String timeStr) {
	    // "HH:mm"形式に必要な最小桁数(5文字)に満たない場合はnull
	    if (timeStr == null || timeStr.length() < 5) {
	        return null;
	    }
	    try {
	        // インデックス0～1(先頭の2桁:"HH")を切り出して「時間」に変換
	        return Integer.parseInt(timeStr.substring(0, 2));
	    } catch (NumberFormatException e) {
	        return null;
	    }
	}
	
	//task.26
	/**
	 * 時刻文字列("HH:mm")から「分」を抽出する。
	 *
	 * @param timeStr 時刻文字列(例:"09:15")
	 * @return 分(Integer)。取得できない場合はnull
	 */
	public Integer getMinute(String timeStr) {
	    // "HH:mm"形式に必要な最小桁数(5文字)に満たない場合はnull
	    if (timeStr == null || timeStr.length() < 5) {
	        return null;
	    }
	    try {
	        // インデックス3～4(末尾の2桁:"mm")を切り出して「分」に変換
	        return Integer.parseInt(timeStr.substring(3, 5));
	    } catch (NumberFormatException e) {
	        return null;
	    }
	}
	
}
