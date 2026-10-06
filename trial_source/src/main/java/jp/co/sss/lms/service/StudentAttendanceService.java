package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		//Task.26 時間マップと分マップを生成してフォームセット
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinuteMap());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			//task.26 出退勤時間を「時間」「分」に分解して、プルダウンの初期値としてセット。
			//出勤時間（時間・分）
			dailyAttendanceForm.setTrainingStartTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime())); //DTOから出勤時間の文字列を取り出す
			dailyAttendanceForm.setTrainingStartTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));
			//退勤時間（時間・分）
			dailyAttendanceForm.setTrainingEndTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm.setTrainingEndTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	//task.26
	/**
	 * フォーム内の「時間」と「分」の入力を、"HH:mm"形式の文字列に変換してセットする。
	 * 
	 * <p>
	 * 出勤・退勤それぞれについて、時間と分がともに入力されている場合のみ、
	 * "HH:mm"形式に整形して、対応する時刻フィールドにセットする。
	 * 
	 * @param attendanceForm 勤怠フォーム
	 */
	public void formatConversion(AttendanceForm attendanceForm) {
		//リストがなければ何もしない
		if (attendanceForm.getAttendanceList() == null) {
			return;
		}

		// 出勤の「時間」「分」がともに入力されている場合、"HH:mm"にして出勤時刻にセット。
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) { //リストの各日付に対して時:分→"HH:mm"の変換処理を繰り返す。
			if (dailyAttendanceForm.getTrainingStartTimeHour() != null
					&& dailyAttendanceForm.getTrainingStartTimeMinute() != null) { //時:分が両方あるときだけ変換するための条件式。
				String startTime = String.format("%02d:%02d", //%02d→9:05のように入力した場合、09:05のようにフォーマットを直すため。
						dailyAttendanceForm.getTrainingStartTimeHour(),
						dailyAttendanceForm.getTrainingStartTimeMinute());

				dailyAttendanceForm.setTrainingStartTime(startTime);

			}

			// 退勤の「時間」「分」がともに入力されている場合、"HH:mm"にして退勤時刻にセット。
			if (dailyAttendanceForm.getTrainingEndTimeHour() != null
					&& dailyAttendanceForm.getTrainingEndTimeMinute() != null) {
				String endTime = String.format("%02d:%02d",
						dailyAttendanceForm.getTrainingEndTimeHour(),
						dailyAttendanceForm.getTrainingEndTimeMinute());

				dailyAttendanceForm.setTrainingEndTime(endTime);

			}
		}
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	//task.25
	/**
	 * 過去の勤怠未入力データの有無をチェックします。
	 * 
	 * <p>本日より前の研修日において、出退勤時間が未入力となっている。
	 * 勤怠データが存在するかどうかを確認します。
	 * @return 未入力の勤怠データが存在する場合はtrue、存在しない場合はfalse
	 * @throws ParseException　日付パースの処理に失敗した場合
	 */
	public boolean notEnterCheck() throws ParseException {

		//Utilから時刻をリセットした本日日付を取得
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 未入力件数をカウント。
		Integer count = tStudentAttendanceMapper.notEnterCount(
				loginUserDto.getLmsUserId(),
				Constants.DB_FLG_FALSE, //削除されたデータも未入力として扱わないように。
				trainingDate);
		//0より大きければtrue、それ以外はfalse
		return count > 0;

	}

	//task.27
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {
		// インデックス管理用の変数（エラーの一要素を特定するため）
		int index = 0;
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			String note = dailyAttendanceForm.getNote();
			Integer startHour = dailyAttendanceForm.getTrainingStartTimeHour();
			Integer startMin = dailyAttendanceForm.getTrainingStartTimeMinute();
			Integer endHour = dailyAttendanceForm.getTrainingEndTimeHour();
			Integer endMin = dailyAttendanceForm.getTrainingEndTimeMinute();
			Integer blankTime = dailyAttendanceForm.getBlankTime();

			// a.備考の文字数チェック(>100)
			if (note != null && note.length() > 100) {
				result.rejectValue("attendanceList[" + index + "]).note", "maxlength", new Object[] { "備考", "100" },
						null);
			}

			//b. 出勤時間の片側未入力チェック
			if ((startHour != null && startMin == null) || (startHour == null && startMin != null)) {
				result.rejectValue("attendanceList[" + index + "].trainingStartTimeHour", "input.invalid",
						new Object[] { "出勤時間" }, null);
			}

			//c. 退勤時間の片側未入力チェック
			if ((endHour != null && endMin == null) || (endHour == null && endMin != null)) {
				result.rejectValue("attendanceList[" + index + "].trainingEndTimeHour", "input.invalid",
						new Object[] { "退勤時間" }, null);
			}
			//d. 出勤時間なし＆退勤時間ありの矛盾チェック
			boolean isStartEmpty = (startHour == null && startMin == null);
			boolean isEndEntered = (endHour != null && endMin !=null);
			if(isStartEmpty && isEndEntered) {
				result.rejectValue("attendanceList[" + index + "].trainingStartTomeHour", "attendance.punchInEmpty", null , null);	
			}
			
			// 出勤・退勤の両方が時間・分ともにそろっている場合のみ時刻計算を行う
			boolean isStartComplete = (startHour != null && startMin != null);
			boolean isEndComplete = (endHour != null && endMin != null);
			
			if(isStartComplete && isEndComplete) {
				int startTotalMin = startHour * 60 + startMin;
				int endTotalMin = endHour * 60 + endMin;
				
				//e. 出勤時間>退勤時間の比較チェック
				if(startTotalMin > endTotalMin) {
					result.rejectValue("attendanceList[" + index + "].trainingStartTimeHour", "attendance.trainingTimeRange", new Object[] { index }, null);
				} else {
					// f. 中抜け時間が勤務時間(出勤〜退勤)を超えるかチェック
					if (blankTime != null) {
	                    int workTimeMin = endTotalMin - startTotalMin;
	                    if (blankTime > workTimeMin) {
	                        result.rejectValue("attendanceList[" + index + "].blankTime", "attendance.blankTimeError", null, null);
	                    }
	                }
				}
			}
			index++;

		}

	}
}