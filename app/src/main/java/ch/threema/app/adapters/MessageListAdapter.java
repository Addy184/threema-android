/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.ConversationTagService;
import ch.threema.app.services.ConversationTagServiceImpl;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.CountBoxView;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.app.voip.groupcall.GroupCallDescription;
import ch.threema.app.voip.groupcall.GroupCallManager;
import ch.threema.app.voip.groupcall.GroupCallObserver;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.TagModel;

public class MessageListAdapter extends AbstractRecyclerAdapter<ConversationModel, RecyclerView.ViewHolder> {

	private static final int MAX_SELECTED_ITEMS = 0;

	public static final int TYPE_ITEM = 0;
	public static final int TYPE_FOOTER = 1;

	private final Context context;
	private final GroupService groupService;
	private final GroupCallManager groupCallManager;
	private final ConversationTagService conversationTagService;
	private final ContactService contactService;
	private final DistributionListService distributionListService;
	private final DeadlineListService mutedChatsListService, hiddenChatsListService, mentionOnlyChatsListService;
	private final RingtoneService ringtoneService;
	private final ConversationService conversationService;
	private final EmojiMarkupUtil emojiMarkupUtil;
	private final StateBitmapUtil stateBitmapUtil;
	private @ColorInt final int regularColor;
	private @ColorInt final int ackColor;
	private @ColorInt final int decColor;
	private @ColorInt final int backgroundColor;
	private final boolean isTablet;
	private final LayoutInflater inflater;
	private final ItemClickListener clickListener;
	private final List<ConversationModel> selectedChats = new ArrayList<>();
	private String highlightUid;
	private RecyclerView recyclerView;

	private final TagModel starTagModel, unreadTagModel;

	public static class MessageListViewHolder extends RecyclerView.ViewHolder implements GroupCallObserver {

		TextView fromView;
		protected TextView dateView;
		TextView subjectView;
		ImageView deliveryView, attachmentView, pinIcon;
		View listItemFG;
		View latestMessageContainer;
		View typingContainer;
		TextView groupMemberName;
		CountBoxView unreadCountView;
		View unreadIndicator;
		ImageView muteStatus;
		ImageView hiddenStatus;
		protected AvatarView avatarView;
		protected ConversationModel conversationModel;
		AvatarListItemHolder avatarListItemHolder;
		final View tagStarOn;
		final GroupCallManager groupCallManager;

		private final View ongoingGroupCallContainer;
		private final Chip joinGroupCallButton;
		private final TextView ongoingCallDivider, ongoingCallText;
		private final Chronometer groupCallDuration;

		MessageListViewHolder(final View itemView, final GroupCallManager groupCallManager) {
			super(itemView);

			tagStarOn = itemView.findViewById(R.id.tag_star_on);

			fromView = itemView.findViewById(R.id.from);
			dateView = itemView.findViewById(R.id.date);
			subjectView = itemView.findViewById(R.id.subject);
			unreadCountView = itemView.findViewById(R.id.unread_count);
			avatarView = itemView.findViewById(R.id.avatar_view);
			attachmentView = itemView.findViewById(R.id.attachment);
			deliveryView = itemView.findViewById(R.id.delivery);
			listItemFG = itemView.findViewById(R.id.list_item_fg);
			latestMessageContainer = itemView.findViewById(R.id.latest_message_container);
			typingContainer = itemView.findViewById(R.id.typing_container);
			groupMemberName = itemView.findViewById(R.id.group_member_name);
			unreadIndicator = itemView.findViewById(R.id.unread_view);
			muteStatus = itemView.findViewById(R.id.mute_status);
			hiddenStatus = itemView.findViewById(R.id.hidden_status);
			pinIcon = itemView.findViewById(R.id.pin_icon);
			avatarListItemHolder = new AvatarListItemHolder();
			avatarListItemHolder.avatarView = avatarView;
			avatarListItemHolder.avatarLoadingAsyncTask = null;
			ongoingGroupCallContainer = itemView.findViewById(R.id.ongoing_group_call_container);
			ongoingCallText = itemView.findViewById(R.id.ongoing_call_text);
			joinGroupCallButton = itemView.findViewById(R.id.join_group_call_button);
			ongoingCallDivider = itemView.findViewById(R.id.ongoing_call_divider);
			groupCallDuration = itemView.findViewById(R.id.group_call_duration);

			this.groupCallManager = groupCallManager;
		}

		@Override
		public void onGroupCallUpdate(@Nullable GroupCallDescription call) {
			if (ConfigUtils.isGroupCallsEnabled()) {
				if (call != null && isMatchingGroup(call.getGroupIdInt()) && isNotPrivate()) {
					updateGroupCallDuration(call);
				} else {
					stopGroupCallDuration();
				}
				ListenerManager.conversationListeners.handle(listener -> listener.onModified(conversationModel, null));
			}
		}

		@AnyThread
		private void updateGroupCallDuration(@NonNull GroupCallDescription call) {
			Long runningSince = call.getRunningSince();
			if (runningSince == null) {
				stopGroupCallDuration();
			} else {
				startGroupCallDuration(runningSince);
			}
		}

		@AnyThread
		private void startGroupCallDuration(long base) {
			RuntimeUtil.runOnUiThread(() -> {
				groupCallDuration.setBase(base);
				groupCallDuration.start();
				groupCallDuration.setVisibility(View.VISIBLE);
				ongoingCallDivider.setVisibility(View.VISIBLE);
			});
		}

		@AnyThread
		private void stopGroupCallDuration() {
			RuntimeUtil.runOnUiThread(() -> {
				groupCallDuration.stop();
				groupCallDuration.setVisibility(View.GONE);
				ongoingCallDivider.setVisibility(View.GONE);
			});
		}

		private boolean isMatchingGroup(int groupId) {
			return conversationModel.isGroupConversation() && conversationModel.getGroup().getId() == groupId;
		}

		private boolean isNotPrivate() {
			return hiddenStatus.getVisibility() != View.VISIBLE;
		}

		public View getItem() {
			return itemView;
		}

		public ConversationModel getConversationModel() { return conversationModel; }

		@Override
		public void onGroupCallStart(@NonNull GroupModel groupModel, @Nullable GroupCallDescription call) {
			ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
				@Override
				public void handle(ConversationListener listener) {
					listener.onModified(conversationModel, null);
				}
			});
		}
	}

	public static class FooterViewHolder extends RecyclerView.ViewHolder {
		FooterViewHolder(View itemView) {
			super(itemView);
		}
	}

	public interface ItemClickListener {
		void onItemClick(View view, int position, ConversationModel conversationModel);
		boolean onItemLongClick(View view, int position, ConversationModel conversationModel);
		void onAvatarClick(View view, int position, ConversationModel conversationModel);
		void onFooterClick(View view);
		void onJoinGroupCallClick(ConversationModel conversationModel);
	}

	public MessageListAdapter(
		Context context,
		ContactService contactService,
		GroupService groupService,
		GroupCallManager groupCallManager,
		DistributionListService distributionListService,
		ConversationService conversationService,
		DeadlineListService mutedChatsListService,
		DeadlineListService mentionOnlyChatsListService,
		DeadlineListService hiddenChatsListService,
		ConversationTagService conversationTagService,
		RingtoneService ringtoneService,
		String highlightUid,
		ItemClickListener clickListener) {

		this.context = context;
		this.inflater = LayoutInflater.from(context);
		this.contactService = contactService;
		this.groupService = groupService;
		this.conversationTagService = conversationTagService;
		this.emojiMarkupUtil = EmojiMarkupUtil.getInstance();
		this.stateBitmapUtil = StateBitmapUtil.getInstance();
		this.distributionListService = distributionListService;
		this.conversationService = conversationService;
		this.mutedChatsListService = mutedChatsListService;
		this.mentionOnlyChatsListService = mentionOnlyChatsListService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.ringtoneService = ringtoneService;
		this.highlightUid = highlightUid;
		this.clickListener = clickListener;

		this.regularColor = ConfigUtils.getColorFromAttribute(context, android.R.attr.textColorSecondary);
		this.backgroundColor = ConfigUtils.getColorFromAttribute(context, android.R.attr.windowBackground);

		this.ackColor = context.getResources().getColor(R.color.material_green);
		this.decColor = context.getResources().getColor(R.color.material_orange);

		this.isTablet = ConfigUtils.isTabletLayout();

		this.starTagModel = this.conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);
		this.unreadTagModel = this.conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_UNREAD);

		this.groupCallManager = groupCallManager;
	}

	@Override
	public int getItemViewType(int position) {
		return position >= super.getItemCount() ? TYPE_FOOTER : TYPE_ITEM;
	}

	@Override
	public int getItemCount() {
		int count = super.getItemCount();

		if (count > 0) {
			return count + 1;
		} else {
			return 1;
		}
	}

	@Override
	public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
		super.onViewRecycled(holder);
		if (holder instanceof MessageListViewHolder && ((MessageListViewHolder) holder).conversationModel.isGroupConversation()) {
			MessageListViewHolder messageListViewHolder = (MessageListViewHolder) holder;
			GroupModel group = messageListViewHolder.conversationModel.getGroup();
			groupCallManager.removeGroupCallObserver(group, messageListViewHolder);
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
		if (viewType == TYPE_ITEM) {
			View itemView = inflater.inflate(R.layout.item_message_list, viewGroup, false);
			itemView.setClickable(true);
			// TODO: MaterialCardView: Setting a custom background is not supported.
			itemView.setBackgroundResource(R.drawable.listitem_background_selector);
			return new MessageListViewHolder(itemView, groupCallManager);
		}
		return new FooterViewHolder(inflater.inflate(R.layout.footer_message_section, viewGroup, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
		if (h instanceof MessageListViewHolder) {
			final MessageListViewHolder holder = (MessageListViewHolder) h;
			final int position = h.getAdapterPosition();

			final ConversationModel conversationModel = this.getEntity(position);
			holder.conversationModel = conversationModel;

			holder.itemView.setOnClickListener(new DebouncedOnClickListener(500) {
				@Override
				public void onDebouncedClick(View v) {
					// position may have changed after the item was bound. query current position from holder
					int currentPos = holder.getLayoutPosition();

					if (currentPos >= 0) {
						clickListener.onItemClick(v, currentPos, getEntity(currentPos));
					}
				}
			});

			holder.itemView.setOnLongClickListener(v -> {
				// position may have changed after the item was bound. query current position from holder
				int currentPos = holder.getLayoutPosition();

				if (currentPos >= 0) {
					return clickListener.onItemLongClick(v, currentPos, getEntity(currentPos));
				}
				return false;
			});

			holder.avatarView.setOnClickListener(v -> {
				// position may have changed after the item was bound. query current position from holder
				int currentPos = holder.getLayoutPosition();

				if (currentPos >= 0) {
					clickListener.onAvatarClick(v, currentPos, getEntity(currentPos));
				}
			});

			holder.joinGroupCallButton.setOnClickListener(v -> {
				// position may have changed after the item was bound. query current position from holder
				int currentPos = holder.getLayoutPosition();

				if (currentPos >= 0) {
					clickListener.onJoinGroupCallClick(getEntity(currentPos));
				}
			});

			// Show or hide star tag
			boolean isTagStarOn = this.conversationTagService.isTaggedWith(conversationModel, this.starTagModel);
			ViewUtil.show(holder.tagStarOn, isTagStarOn);
			ViewUtil.show(holder.pinIcon, isTagStarOn);

			AbstractMessageModel messageModel = conversationModel.getLatestMessage();

			if (holder.groupMemberName != null) {
				holder.groupMemberName.setVisibility(View.GONE);
			}

			holder.fromView.setText(conversationModel.getReceiver().getDisplayName());

			if (messageModel != null && ((!messageModel.isOutbox() && conversationModel.hasUnreadMessage()) || this.conversationTagService.isTaggedWith(conversationModel, this.unreadTagModel))) {
				holder.fromView.setTextAppearance(context, R.style.Threema_TextAppearance_List_FirstLine_Bold);
				holder.subjectView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine_Bold);
				if (holder.groupMemberName != null && holder.dateView != null) {
					holder.groupMemberName.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine_Bold);
				}
				long unreadCount = conversationModel.getUnreadCount();
				if (unreadCount > 0) {
					holder.unreadCountView.setText(String.valueOf(unreadCount));
					holder.unreadCountView.setVisibility(View.VISIBLE);
					holder.unreadIndicator.setVisibility(View.VISIBLE);
				} else if (this.conversationTagService.isTaggedWith(conversationModel, this.unreadTagModel)) {
					holder.unreadCountView.setText("");
					holder.unreadCountView.setVisibility(View.VISIBLE);
					holder.unreadIndicator.setVisibility(View.VISIBLE);
				}
			} else {
				holder.fromView.setTextAppearance(context, R.style.Threema_TextAppearance_List_FirstLine);
				holder.subjectView.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
				if (holder.groupMemberName != null && holder.dateView != null) {
					holder.groupMemberName.setTextAppearance(context, R.style.Threema_TextAppearance_List_SecondLine);
				}
				holder.unreadCountView.setVisibility(View.GONE);
				holder.unreadIndicator.setVisibility(View.GONE);
			}

			holder.deliveryView.setColorFilter(this.regularColor);
			holder.attachmentView.setColorFilter(this.regularColor);
			holder.muteStatus.setColorFilter(this.regularColor);
			holder.dateView.setTextAppearance(context, R.style.Threema_TextAppearance_List_ThirdLine);
			holder.subjectView.setVisibility(View.VISIBLE);

			String uniqueId = conversationModel.getReceiver().getUniqueIdString();

			if (messageModel != null) {
				if (hiddenChatsListService.has(uniqueId)) {
					holder.hiddenStatus.setVisibility(View.VISIBLE);
					holder.subjectView.setText(R.string.private_chat_subject);
					holder.attachmentView.setVisibility(View.GONE);
					holder.dateView.setVisibility(View.INVISIBLE);
					holder.deliveryView.setVisibility(View.GONE);
					holder.joinGroupCallButton.setVisibility(View.GONE);
					holder.ongoingGroupCallContainer.setVisibility(View.GONE);
				} else {
					holder.hiddenStatus.setVisibility(View.GONE);
					holder.dateView.setText(MessageUtil.getDisplayDate(this.context, messageModel, false));
					holder.dateView.setContentDescription("." + context.getString(R.string.state_dialog_modified) + "." + holder.dateView.getText() + ".");
					holder.dateView.setVisibility(View.VISIBLE);

					String draft = ThreemaApplication.getMessageDraft(uniqueId);
					if (!TestUtil.empty(draft)) {
						holder.groupMemberName.setVisibility(View.GONE);
						holder.attachmentView.setVisibility(View.GONE);
						holder.deliveryView.setVisibility(View.GONE);
						holder.dateView.setText(" " + context.getString(R.string.draft));
						holder.dateView.setContentDescription(null);
						holder.dateView.setTextAppearance(context, R.style.Threema_TextAppearance_List_ThirdLine_Red);
						holder.dateView.setVisibility(View.VISIBLE);
						holder.subjectView.setText(emojiMarkupUtil.formatBodyTextString(context, draft + " ", 100));
					} else {
						if (conversationModel.isGroupConversation()) {
							if (holder.groupMemberName != null && messageModel.getType() != MessageType.GROUP_CALL_STATUS) {
								holder.groupMemberName.setText(NameUtil.getShortName(this.context, messageModel, this.contactService) + ": ");
								holder.groupMemberName.setVisibility(View.VISIBLE);
							}
						} else {
							holder.joinGroupCallButton.setVisibility(View.GONE);
							holder.ongoingGroupCallContainer.setVisibility(View.GONE);
						}

						// Configure subject
						MessageUtil.MessageViewElement viewElement = MessageUtil.getViewElement(this.context, messageModel);
						String subject = viewElement.text;

						if (messageModel.getType() == MessageType.TEXT) {
							// we need to add an arbitrary character - otherwise span-only strings are formatted incorrectly in the item layout
							subject += " ";
						}

						if (viewElement.icon != null) {
							holder.attachmentView.setVisibility(View.VISIBLE);
							holder.attachmentView.setImageResource(viewElement.icon);
							if (viewElement.placeholder != null) {
								holder.attachmentView.setContentDescription(viewElement.placeholder);
							} else {
								holder.attachmentView.setContentDescription("");
							}

							// Configure attachment
							// Configure color of the attachment view
							if (viewElement.color != null) {
								holder.attachmentView.setColorFilter(
										this.context.getResources().getColor(viewElement.color),
										PorterDuff.Mode.SRC_IN);
							}
						} else {
							holder.attachmentView.setVisibility(View.GONE);
						}

						if (TestUtil.empty(subject)) {
							holder.subjectView.setText("");
							holder.subjectView.setContentDescription("");
						} else {
							// Append space if attachmentView is visible
							if (holder.attachmentView.getVisibility() == View.VISIBLE) {
								subject = " " + subject;
							}
							holder.subjectView.setText(emojiMarkupUtil.formatBodyTextString(context, subject, 100));
							holder.subjectView.setContentDescription(viewElement.contentDescription);
						}

						// Special icons for voice call message
						if (messageModel.getType() == MessageType.VOIP_STATUS) {
							// Always show the phone icon
							holder.deliveryView.setImageResource(R.drawable.ic_phone_locked);
						} else if (messageModel.getType() == MessageType.GROUP_CALL_STATUS) {
							holder.deliveryView.setImageResource(R.drawable.ic_group_call);
						} else {
							if (!messageModel.isOutbox()) {
								holder.deliveryView.setImageResource(R.drawable.ic_reply_filled);
								holder.deliveryView.setContentDescription(context.getString(R.string.state_sent));

								if (conversationModel.isContactConversation()){
									if (messageModel.getState() != null) {
										switch (messageModel.getState()) {
											case USERACK:
												holder.deliveryView.setColorFilter(this.ackColor);
												break;
											case USERDEC:
												holder.deliveryView.setColorFilter(this.decColor);
												break;
										}
									}
								}
								holder.deliveryView.setVisibility(View.VISIBLE);
							} else {
								stateBitmapUtil.setStateDrawable(messageModel, holder.deliveryView, false);
							}
						}

						if (conversationModel.isGroupConversation()) {
							if (groupService.isNotesGroup(conversationModel.getGroup())) {
								holder.deliveryView.setImageResource(R.drawable.ic_spiral_bound_booklet_outline);
								holder.deliveryView.setContentDescription(context.getString(R.string.notes));
							} else {
								holder.deliveryView.setImageResource(R.drawable.ic_group_filled);
								holder.deliveryView.setContentDescription(context.getString(R.string.prefs_group_notifications));
							}
							holder.deliveryView.setVisibility(View.VISIBLE);
						} else if (conversationModel.isDistributionListConversation()) {
							holder.deliveryView.setImageResource(R.drawable.ic_distribution_list_filled);
							holder.deliveryView.setContentDescription(context.getString(R.string.distribution_list));
							holder.deliveryView.setVisibility(View.VISIBLE);
						}
					}
				}
				if (mutedChatsListService.has(uniqueId)) {
					holder.muteStatus.setImageResource(R.drawable.ic_do_not_disturb_filled);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else if (mentionOnlyChatsListService.has(uniqueId)) {
					holder.muteStatus.setImageResource(R.drawable.ic_dnd_mention_black_18dp);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else if (ringtoneService.hasCustomRingtone(uniqueId) && ringtoneService.isSilent(uniqueId, conversationModel.isGroupConversation())) {
					holder.muteStatus.setImageResource(R.drawable.ic_notifications_off_filled);
					holder.muteStatus.setVisibility(View.VISIBLE);
				} else {
					holder.muteStatus.setVisibility(View.GONE);
				}
			} else {
				// empty chat
				holder.attachmentView.setVisibility(View.GONE);
				holder.deliveryView.setVisibility(View.GONE);
				holder.dateView.setVisibility(View.GONE);
				holder.dateView.setContentDescription(null);
				holder.subjectView.setText("");
				holder.subjectView.setContentDescription("");
				holder.muteStatus.setVisibility(View.GONE);
				holder.hiddenStatus.setVisibility(uniqueId != null && hiddenChatsListService.has(uniqueId) ? View.VISIBLE : View.GONE);
				holder.joinGroupCallButton.setVisibility(View.GONE);
				holder.ongoingGroupCallContainer.setVisibility(View.GONE);
			}

			initializeGroupCallIndicator(holder, conversationModel);

			AdapterUtil.styleConversation(holder.fromView, groupService, conversationModel);

			AvatarListItemUtil.loadAvatar(conversationModel, contactService, groupService, distributionListService, holder.avatarListItemHolder);

			this.updateTypingIndicator(
					holder,
					conversationModel.isTyping()
			);

			holder.itemView.setActivated(selectedChats.contains(conversationModel));

			if (isTablet) {
				// handle selection in multi-pane mode
				if (highlightUid != null && highlightUid.equals(conversationModel.getUid()) && context instanceof ComposeMessageActivity) {
					if (ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK) {
						holder.listItemFG.setBackgroundResource(R.color.dark_settings_multipane_selection_bg);
					} else {
						holder.listItemFG.setBackgroundResource(R.color.settings_multipane_selection_bg);
					}
				} else {
					holder.listItemFG.setBackgroundColor(this.backgroundColor);
				}
			}
		} else {
			// footer
			Chip archivedChip = h.itemView.findViewById(R.id.archived_text);

			int archivedCount = conversationService.getArchivedCount();
			if (archivedCount > 0) {
				archivedChip.setVisibility(View.VISIBLE);
				archivedChip.setOnClickListener(clickListener::onFooterClick);
				archivedChip.setText(ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.num_archived_chats, archivedCount, archivedCount));
				if (recyclerView != null) {
					((EmptyRecyclerView) recyclerView).setNumHeadersAndFooters(0);
				}
			} else {
				archivedChip.setVisibility(View.GONE);
				if (recyclerView != null) {
					((EmptyRecyclerView) recyclerView).setNumHeadersAndFooters(1);
				}
			}
		}
	}

	/**
	 * Initializes the view holder regarding ongoing group calls. If a group call is running, it
	 * makes the join group call button visible and disables all the views that would be hidden
	 * by the button.
	 */
	private void initializeGroupCallIndicator(@NonNull MessageListViewHolder holder, @NonNull ConversationModel conversationModel) {
		GroupModel groupModel = conversationModel.getGroup();
		if (conversationModel.isGroupConversation()
			&& !groupService.isNotesGroup(groupModel)
			&& groupService.isGroupMember(groupModel)
		) {
			GroupCallDescription call = groupCallManager.getCurrentChosenCall(holder.conversationModel.getGroup());
			if (call != null && ConfigUtils.isGroupCallsEnabled()) {
				boolean isJoined = groupCallManager.isJoinedCall(call);

				holder.joinGroupCallButton.setVisibility(View.VISIBLE);
				holder.joinGroupCallButton.setText(isJoined ? R.string.voip_gc_open_call : R.string.voip_gc_join_call);
				ColorStateList groupCallTextColor = ColorStateList.valueOf(context.getResources().getColor(R.color.group_call_accent));
				holder.joinGroupCallButton.setTextColor(groupCallTextColor);
				holder.joinGroupCallButton.setChipBackgroundColor(groupCallTextColor.withAlpha(0x1a));
				holder.ongoingCallText.setText(isJoined ? R.string.voip_gc_in_call : R.string.voip_gc_ongoing_call);
				holder.ongoingGroupCallContainer.setVisibility(View.VISIBLE);

				holder.unreadCountView.setVisibility(View.GONE);
				holder.pinIcon.setVisibility(View.GONE);
				holder.typingContainer.setVisibility(View.GONE);
				holder.deliveryView.setVisibility(View.GONE);
				holder.subjectView.setVisibility(View.GONE);
				holder.dateView.setVisibility(View.GONE);
				holder.attachmentView.setVisibility(View.GONE);
				holder.groupMemberName.setVisibility(View.GONE);
				holder.muteStatus.setVisibility(View.GONE);
			} else {
				holder.joinGroupCallButton.setVisibility(View.GONE);
				holder.ongoingGroupCallContainer.setVisibility(View.GONE);
			}
			groupCallManager.addGroupCallObserver(groupModel, holder);
		} else {
			if (groupModel != null) {
				groupCallManager.removeGroupCallObserver(groupModel, holder);
			}
			holder.joinGroupCallButton.setVisibility(View.GONE);
			holder.ongoingGroupCallContainer.setVisibility(View.GONE);
		}
	}

	@Override
	public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
		super.onAttachedToRecyclerView(recyclerView);

		this.recyclerView = recyclerView;
	}

	@Override
	public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
		this.recyclerView = null;

		super.onDetachedFromRecyclerView(recyclerView);
	}

	public void toggleItemChecked(ConversationModel model, int position) {
		if (selectedChats.contains(model)) {
			selectedChats.remove(model);
		} else if (selectedChats.size() <= MAX_SELECTED_ITEMS) {
			selectedChats.add(model);
		}
		notifyItemChanged(position);
	}

	public void clearSelections() {
		selectedChats.clear();
		notifyDataSetChanged();
	}

	public int getCheckedItemCount() {
		return selectedChats.size();
	}

	public void refreshFooter() {
		notifyItemChanged(getItemCount() - 1);
	}

	public List<ConversationModel> getCheckedItems() {
		return selectedChats;
	}

	public void setHighlightItem(String uid) {
		highlightUid = uid;
	}

	private void updateTypingIndicator(MessageListViewHolder holder, boolean isTyping) {
		if(holder != null && holder.latestMessageContainer != null && holder.typingContainer != null) {
			holder.latestMessageContainer.setVisibility(isTyping ? View.GONE : View.VISIBLE);
			holder.typingContainer.setVisibility(!isTyping ? View.GONE : View.VISIBLE);
		}
	}
}
