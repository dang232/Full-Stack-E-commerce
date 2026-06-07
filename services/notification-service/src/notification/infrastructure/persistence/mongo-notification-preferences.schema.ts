import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document } from 'mongoose';

@Schema({ _id: false })
class TypePreferenceSubdoc {
  @Prop({ required: true })
  type!: string;

  @Prop({ type: [String], required: true })
  channels!: string[];
}

const TypePreferenceSubdocSchema =
  SchemaFactory.createForClass(TypePreferenceSubdoc);

@Schema({ collection: 'notification_preferences', timestamps: false })
export class NotificationPreferencesSchemaClass extends Document {
  @Prop({ required: true, unique: true, index: true })
  userId!: string;

  @Prop({ type: [TypePreferenceSubdocSchema], default: [] })
  typePreferences!: TypePreferenceSubdoc[];

  @Prop({ default: false })
  muted!: boolean;

  @Prop({ default: () => new Date() })
  updatedAt!: Date;
}

export const NotificationPreferencesSchema = SchemaFactory.createForClass(
  NotificationPreferencesSchemaClass,
);
